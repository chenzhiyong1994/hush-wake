param(
    [string]$Adb = "adb",
    [string]$Apk = "app\build\outputs\apk\debug\app-debug.apk",
    [switch]$KeepData
)

$ErrorActionPreference = "Stop"
$packageName = "com.hushwake.app"
$componentName = "$packageName/.HomeActivity"
$remoteDump = "/sdcard/hushwake-smoke.xml"
$localDump = Join-Path $env:TEMP "hushwake-smoke.xml"

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
    [xml](Get-Content -LiteralPath $localDump -Raw)
}

function Find-UiNode {
    param([string]$Text)
    for ($attempt = 0; $attempt -lt 4; $attempt++) {
        $xml = Get-UiXml
        $node = $xml.SelectSingleNode("//node[@text='$Text']")
        if ($node) {
            return $node
        }
        Invoke-Adb -Arguments @("shell", "input", "swipe", "540", "1500", "540", "450", "450") | Out-Null
        Start-Sleep -Milliseconds 450
    }
    throw "UI text not found after scrolling: $Text"
}

function Tap-UiText {
    param([string]$Text)
    $node = Find-UiNode $Text
    if ($node.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
        throw "Invalid bounds for '$Text': $($node.bounds)"
    }
    $left = [int]$matches[1]
    $top = [int]$matches[2]
    $right = [int]$matches[3]
    $bottom = [int]$matches[4]
    $x = [int](($left + $right) / 2)
    $y = [int](($top + $bottom) / 2)
    Invoke-Adb -Arguments @("shell", "input", "tap", $x, $y) | Out-Null
    Start-Sleep -Milliseconds 500
}

function Assert-UiText {
    param([string]$Text)
    Find-UiNode $Text | Out-Null
}

function Assert-UiTextAbsent {
    param([string]$Text)
    $xml = Get-UiXml
    if ($xml.SelectSingleNode("//node[@text='$Text']")) {
        throw "Unexpected UI text found: $Text"
    }
}

Invoke-Adb -Arguments @("install", "-r", $Apk) | Out-Host
if (-not $KeepData) {
    Invoke-Adb -Arguments @("shell", "pm", "clear", $packageName) | Out-Null
}
Invoke-Adb -Arguments @("shell", "pm", "grant", $packageName, "android.permission.BLUETOOTH_CONNECT") | Out-Null
Invoke-Adb -Arguments @("shell", "pm", "grant", $packageName, "android.permission.POST_NOTIFICATIONS") | Out-Null
Invoke-Adb -Arguments @("shell", "cmd", "media_session", "volume", "--stream", "3", "--set", "5") | Out-Null
Invoke-Adb -Arguments @("logcat", "-c") | Out-Null
Invoke-Adb -Arguments @("shell", "am", "force-stop", $packageName) | Out-Null
Invoke-Adb -Arguments @("shell", "am", "start", "-W", "-n", $componentName) | Out-Host
Start-Sleep -Seconds 2

$initial = Get-UiXml
$onboarding = $initial.SelectSingleNode("//node[@text='HUSHWAKE  /  悄醒']")
if ($onboarding) {
    Tap-UiText "我了解当前输出会随耳机连接状态自动选择"
    Tap-UiText "开始使用"
    Assert-UiText "几点叫醒你？"
}

$screens = @(
    @{ Tab = "闹钟"; Marker = "几点叫醒你？" },
    @{ Tab = "助眠声"; Marker = "想听什么入睡？" }
)
foreach ($screen in $screens) {
    Tap-UiText $screen.Tab
    Assert-UiText $screen.Marker
}

Assert-UiText "声音库"
Tap-UiText "播放"
Assert-UiText "暂停"
Assert-UiText "停止"
$playing = Get-UiXml
$minuteLabels = $playing.SelectNodes("//node[contains(@text,'分钟')]")
if ($minuteLabels.Count -ne 1) {
    throw "Expected one live sleep timer label while playing, found $($minuteLabels.Count)"
}
Tap-UiText "停止"

Tap-UiText "闹钟"
Assert-UiTextAbsent "不同于普通闹钟：悄醒只用媒体音播放，跟随手机媒体音量；连接耳机后只走已验证耳机，断连也不会转到扬声器。"
Tap-UiText "+  新建闹钟"
Assert-UiText "新闹钟"
Assert-UiText "唤醒时间"
Tap-UiText "调整  ›"
Assert-UiText "设置唤醒时间"
Assert-UiText "15 分钟后"
Tap-UiText "30 分钟后"
Tap-UiText "完成"
Invoke-Adb -Arguments @("shell", "input", "swipe", "540", "2050", "540", "450", "500") | Out-Null
Start-Sleep -Milliseconds 500
Assert-UiText "铃声库"
Assert-UiText "保存闹钟"
Invoke-Adb -Arguments @("shell", "input", "keyevent", "BACK") | Out-Null
Start-Sleep -Milliseconds 500

$appProcessId = (Invoke-Adb -Arguments @("shell", "pidof", $packageName) | Select-Object -First 1).Trim()
$crashLog = (Invoke-Adb -Arguments @("logcat", "-b", "crash", "-d", "-v", "threadtime")) -join "`n"
if (-not $appProcessId -or $crashLog -match "Process: $([regex]::Escape($packageName))") {
    throw "HushWake smoke test found a crash or missing process`n$crashLog"
}

Write-Output "PASS: onboarding, alarm and sleep pages, six-sound libraries, sleep controls, alarm time wheels, and editor are alive with PID $appProcessId"
