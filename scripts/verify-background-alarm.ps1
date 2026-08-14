param(
    [string]$Adb = "adb",
    [string]$Apk = "app\build\outputs\apk\debug\app-debug.apk"
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

& $Adb uninstall $packageName 2>&1 | Out-Null
Invoke-Adb -Arguments @("install", $Apk) | Out-Host
Invoke-Adb -Arguments @("shell", "pm", "grant", $packageName, "android.permission.POST_NOTIFICATIONS") | Out-Null
Invoke-Adb -Arguments @("shell", "pm", "grant", $packageName, "android.permission.BLUETOOTH_CONNECT") | Out-Null
Invoke-Adb -Arguments @("logcat", "-c") | Out-Null
Invoke-Adb -Arguments @("shell", "am", "start", "-W", "-n", "$packageName/.HomeActivity") | Out-Null
Start-Sleep -Seconds 1

$initial = Get-UiXml
if ($initial.SelectSingleNode("//node[@text='HUSHWAKE  /  悄醒']")) {
    Tap-UiText -Text "我了解当前输出会随耳机连接状态自动选择"
    Tap-UiText -Text "开始使用"
}

Tap-UiText -Text "+  新建闹钟"
Find-UiNode -Text "新闹钟" | Out-Null
Tap-UiText -Text "保存闹钟"

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
Invoke-Adb -Arguments @("shell", "am", "kill", $packageName) | Out-Null
Invoke-Adb -Arguments @("root") | Out-Null
Invoke-Adb -Arguments @("wait-for-device") | Out-Null
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
} finally {
    Invoke-Adb -Arguments @("shell", "settings", "put", "global", "auto_time", "1") | Out-Null
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $packageName) | Out-Null
}

Write-Output "PASS: fresh install registered an exact alarm and Android triggered it after the app left foreground."
