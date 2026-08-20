param(
    [string]$Adb = "adb",
    [string]$Apk = "app\build\outputs\apk\debug\app-debug.apk",
    [switch]$KeepBackgroundProcess,
    [switch]$OpenHomeWhileRinging,
    [switch]$SnoozeWhileRinging
)

$ErrorActionPreference = "Stop"
$packageName = "com.hushwake.app"
$remoteDump = "/sdcard/hushwake-background-alarm.xml"
$localDump = Join-Path $env:TEMP "hushwake-background-alarm.xml"

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $output = & $Adb @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed: $($Arguments -join ' ')`n$($output -join "`n")"
    }
    return $output
}

function Get-UiXml {
    Invoke-Adb -Arguments @("shell", "uiautomator", "dump", $remoteDump) | Out-Null
    Invoke-Adb -Arguments @("pull", $remoteDump, $localDump) | Out-Null
    return [xml](Get-Content -LiteralPath $localDump -Raw)
}

function Find-UiNode {
    param([Parameter(Mandatory = $true)][string]$Text)
    for ($attempt = 0; $attempt -lt 6; $attempt++) {
        $node = (Get-UiXml).SelectSingleNode("//node[@text='$Text']")
        if ($node) {
            return $node
        }
        Invoke-Adb -Arguments @("shell", "input", "swipe", "540", "1550", "540", "450", "400") | Out-Null
        Start-Sleep -Milliseconds 350
    }
    throw "UI text not found after scrolling: $Text"
}

function Tap-UiText {
    param([Parameter(Mandatory = $true)][string]$Text)
    $node = Find-UiNode -Text $Text
    if ($node.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
        throw "Invalid bounds for '$Text': $($node.bounds)"
    }
    $x = [int](([int]$matches[1] + [int]$matches[3]) / 2)
    $y = [int](([int]$matches[2] + [int]$matches[4]) / 2)
    Invoke-Adb -Arguments @("shell", "input", "tap", $x, $y) | Out-Null
    Start-Sleep -Milliseconds 500
}

function Tap-FirstAlarmSwitch {
    $node = (Get-UiXml).SelectSingleNode("//node[@class='android.widget.Switch']")
    if (-not $node -or $node.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
        throw "Alarm enable switch was not found."
    }
    $x = [int](([int]$matches[1] + [int]$matches[3]) / 2)
    $y = [int](([int]$matches[2] + [int]$matches[4]) / 2)
    Invoke-Adb -Arguments @("shell", "input", "tap", $x, $y) | Out-Null
    Start-Sleep -Milliseconds 500
}

Invoke-Adb -Arguments @("root") | Out-Null
Invoke-Adb -Arguments @("wait-for-device") | Out-Null
& $Adb uninstall $packageName 2>&1 | Out-Null
Invoke-Adb -Arguments @("install", $Apk) | Out-Host
Invoke-Adb -Arguments @("shell", "pm", "grant", $packageName, "android.permission.POST_NOTIFICATIONS") | Out-Null
Invoke-Adb -Arguments @("shell", "pm", "grant", $packageName, "android.permission.BLUETOOTH_CONNECT") | Out-Null
Invoke-Adb -Arguments @("shell", "cmd", "media_session", "volume", "--stream", "3", "--set", "5") | Out-Null
Invoke-Adb -Arguments @("logcat", "-c") | Out-Null
Invoke-Adb -Arguments @("shell", "am", "start", "-W", "-n", "$packageName/.HomeActivity") | Out-Null
Start-Sleep -Seconds 1

$initial = Get-UiXml
if ($initial.SelectSingleNode("//node[@text='HUSHWAKE  /  悄醒']")) {
    Tap-UiText -Text "我了解当前输出会随耳机连接状态自动选择"
    Tap-UiText -Text "开始使用"
}

Tap-UiText -Text "⊕  新建智能闹钟"
Find-UiNode -Text "新闹钟" | Out-Null
Tap-UiText -Text "保存闹钟"
$afterSave = Get-UiXml
if ($afterSave.SelectSingleNode("//node[@text='立即处理']")) {
    throw "Saving an alarm forced a wake-permission dialog instead of leaving an inline reminder."
}
Find-UiNode -Text "电池优化可能拦截后台唤醒" | Out-Null
Tap-UiText -Text "去允许"
Find-UiNode -Text "Allow" | Out-Null
Tap-UiText -Text "Allow"
$powerAllowlist = (Invoke-Adb -Arguments @("shell", "dumpsys", "deviceidle", "whitelist")) -join "`n"
if ($powerAllowlist -notmatch [regex]::Escape($packageName)) {
    throw "The system accepted the request but the app is not on the battery optimization allowlist."
}

Invoke-Adb -Arguments @("shell", "dumpsys", "deviceidle", "whitelist", "-$packageName") | Out-Null
Tap-FirstAlarmSwitch
Tap-FirstAlarmSwitch
$afterEnable = Get-UiXml
if ($afterEnable.SelectSingleNode("//node[@text='立即处理']")) {
    throw "Enabling an alarm forced a wake-permission dialog instead of leaving an inline reminder."
}
Find-UiNode -Text "电池优化可能拦截后台唤醒" | Out-Null
Tap-UiText -Text "去允许"
Find-UiNode -Text "Allow" | Out-Null
Tap-UiText -Text "Allow"

$alarms = (Invoke-Adb -Arguments @("shell", "dumpsys", "alarm")) -join "`n"
if ($alarms -notmatch 'com\.hushwake\.app\.action\.ALARM_TRIGGER') {
    throw "Fresh install did not register an Android alarm after Save."
}
if ($alarms -notmatch '(?s)com\.hushwake\.app\.action\.ALARM_TRIGGER.*?window=0') {
    throw "Saved alarm is not registered as an exact Android alarm."
}
if ($alarms -notmatch 'triggerTime=(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})') {
    throw "Unable to read the scheduled trigger time from dumpsys alarm."
}
$trigger = [datetime]::ParseExact($matches[1], "yyyy-MM-dd HH:mm:ss", [Globalization.CultureInfo]::InvariantCulture)
$nearTrigger = $trigger.AddSeconds(-15)

Invoke-Adb -Arguments @("shell", "input", "keyevent", "HOME") | Out-Null
if (-not $KeepBackgroundProcess) {
    $appProcess =
        ((Invoke-Adb -Arguments @("shell", "pidof", $packageName)) -join " ").Trim()
    if ($appProcess) {
        $appPid = ($appProcess -split "\s+")[0]
        Invoke-Adb -Arguments @("shell", "kill", "-9", $appPid) | Out-Null
    }
}
Invoke-Adb -Arguments @("shell", "input", "keyevent", "223") | Out-Null
$sleepState = (Invoke-Adb -Arguments @("shell", "dumpsys", "power")) -join "`n"
if ($sleepState -notmatch 'mWakefulness=(Asleep|Dozing)') {
    throw "Device did not enter a sleeping state before the alarm trigger."
}
try {
    Invoke-Adb -Arguments @("shell", "settings", "put", "global", "auto_time", "0") | Out-Null
    $deviceDate = $nearTrigger.ToString("MMddHHmmyyyy.ss", [Globalization.CultureInfo]::InvariantCulture)
    Invoke-Adb -Arguments @("shell", "date", $deviceDate) | Out-Null
    Start-Sleep -Seconds 20

    $services = (Invoke-Adb -Arguments @("shell", "dumpsys", "activity", "services", $packageName)) -join "`n"
    $notifications = (Invoke-Adb -Arguments @("shell", "dumpsys", "notification", "--noredact")) -join "`n"
    if ($services -notmatch 'AlarmRingingService' -or $services -notmatch 'isForeground=true') {
        throw "Alarm did not start its foreground ringing service while the app was in background."
    }
    if ($notifications -notmatch 'pkg=com\.hushwake\.app' -or $notifications -notmatch 'id=4101') {
        throw "Alarm did not post its ringing notification while the app was in background."
    }
    $session =
        (Invoke-Adb -Arguments @(
                "shell",
                "cat",
                "/data/data/$packageName/shared_prefs/hushwake_alarm_session.xml")) -join "`n"
    if ($session -notmatch '<string name="state">AUDIBLE</string>') {
        throw "Alarm service started but playback did not reach the AUDIBLE state.`n$session"
    }
    if ($OpenHomeWhileRinging) {
        Invoke-Adb -Arguments @(
            "shell", "am", "start", "-W",
            "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.LAUNCHER",
            "-n", "$packageName/.HomeActivity") | Out-Null
        Start-Sleep -Seconds 1
        $crashLog =
            (Invoke-Adb -Arguments @("logcat", "-b", "crash", "-d", "-v", "threadtime")) -join "`n"
        if ($crashLog -match "Process: $([regex]::Escape($packageName))") {
            throw "Opening HomeActivity while an alarm is ringing crashed the app.`n$crashLog"
        }
        $runningProcess =
            ((Invoke-Adb -Arguments @("shell", "pidof", $packageName)) -join " ").Trim()
        if (-not $runningProcess) {
            throw "Opening HomeActivity while an alarm is ringing left no app process."
        }
    }
    $activityLog =
        (Invoke-Adb -Arguments @("logcat", "-d", "-v", "brief", "ActivityTaskManager:I", "*:S")) -join "`n"
    if ($activityLog -notmatch 'Displayed com\.hushwake\.app/.alarm\.RingingActivity') {
        $powerAfter = (Invoke-Adb -Arguments @("shell", "dumpsys", "power")) -join "`n"
        $wakefulness =
            [regex]::Match($powerAfter, 'mWakefulness=([^\r\n]+)').Groups[1].Value
        throw "Alarm did not present RingingActivity; wakefulness after trigger: $wakefulness`n$activityLog"
    }
    if ($SnoozeWhileRinging) {
        $beforeSnooze = [long](((Invoke-Adb -Arguments @("shell", "date", "+%s%3N")) -join "").Trim())
        Tap-UiText -Text "稍后 5 分钟"
        Start-Sleep -Seconds 1
        $afterSnooze = [long](((Invoke-Adb -Arguments @("shell", "date", "+%s%3N")) -join "").Trim())
        $row =
            ((Invoke-Adb -Arguments @(
                    "shell",
                    "sqlite3",
                    "/data/data/$packageName/databases/hushwake.db",
                    "'SELECT id,hour,minute,repeat_mask,enabled,snooze_target_epoch_ms FROM alarms ORDER BY id LIMIT 1;'")) -join "").Trim()
        $fields = $row -split '\|'
        if ($fields.Count -ne 6) {
            throw "Unable to read the snoozed alarm row: $row"
        }
        $snoozeTarget = [long]$fields[5]
        if ($fields[3] -ne "0" -or $fields[4] -ne "1") {
            throw "Snooze did not replace the same alarm with an enabled one-time target: $row"
        }
        if ($snoozeTarget -lt ($beforeSnooze + 299000L) -or
                $snoozeTarget -gt ($afterSnooze + 301000L)) {
            throw "Snooze target is not five minutes after the tap: $row"
        }
        $expectedTime = "{0:D2}:{1:D2}" -f [int]$fields[1], [int]$fields[2]
        Invoke-Adb -Arguments @(
            "shell", "am", "start", "-W",
            "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.LAUNCHER",
            "-n", "$packageName/.HomeActivity") | Out-Null
        Start-Sleep -Seconds 1
        if (-not (Get-UiXml).SelectSingleNode("//node[@text='$expectedTime']")) {
            throw "Home did not show the snoozed alarm time $expectedTime."
        }
        $targetAfterReopen =
            ((Invoke-Adb -Arguments @(
                    "shell",
                    "sqlite3",
                    "/data/data/$packageName/databases/hushwake.db",
                    "'SELECT snooze_target_epoch_ms FROM alarms ORDER BY id LIMIT 1;'")) -join "").Trim()
        if ([long]$targetAfterReopen -ne $snoozeTarget) {
            throw "Opening Home changed the persisted snooze target."
        }
        $servicesAfterSnooze =
            (Invoke-Adb -Arguments @("shell", "dumpsys", "activity", "services", $packageName)) -join "`n"
        if ($servicesAfterSnooze -match 'AlarmRingingService') {
            throw "Ringing service was still active after snoozing."
        }
    }
} finally {
    try {
        Invoke-Adb -Arguments @("wait-for-device") | Out-Null
        Invoke-Adb -Arguments @("shell", "settings", "put", "global", "auto_time", "1") | Out-Null
        Invoke-Adb -Arguments @("shell", "am", "force-stop", $packageName) | Out-Null
    } catch {
        Write-Warning "Background-alarm cleanup failed: $($_.Exception.Message)"
    }
}

if ($SnoozeWhileRinging) {
    Write-Output "PASS: background alarm rang; snooze updated the same enabled alarm and survived app reconciliation."
} else {
    Write-Output "PASS: Android triggered an audible alarm and presented its ringing screen after the app left foreground."
}
