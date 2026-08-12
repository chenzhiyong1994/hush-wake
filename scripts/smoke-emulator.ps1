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

Invoke-Adb -Arguments @("install", "-r", $Apk) | Out-Host
if (-not $KeepData) {
    Invoke-Adb -Arguments @("shell", "pm", "clear", $packageName) | Out-Null
}
Invoke-Adb -Arguments @("logcat", "-c") | Out-Null
Invoke-Adb -Arguments @("shell", "am", "force-stop", $packageName) | Out-Null
Invoke-Adb -Arguments @("shell", "am", "start", "-W", "-n", $componentName) | Out-Host
Start-Sleep -Seconds 2

$initial = Get-UiXml
$onboarding = $initial.SelectSingleNode("//node[@text='HUSHWAKE  /  悄醒']")
if ($onboarding) {
    Tap-UiText "我理解：无耳机或无法验证时，闹钟不会发出声音"
    Tap-UiText "进入可靠性中心"
    Assert-UiText "RELIABILITY CENTER  /  分项检查"
}

$screens = @(
    @{ Tab = "闹钟"; Marker = "HUSHWAKE  /  PRIVATE ALARMS" },
    @{ Tab = "白噪音"; Marker = "EAR-SAFE AMBIENCE  /  离线" },
    @{ Tab = "可靠性"; Marker = "RELIABILITY CENTER  /  分项检查" },
    @{ Tab = "记录"; Marker = "LOCAL HISTORY  /  最近 30 次或 30 天" },
    @{ Tab = "设置"; Marker = "SETTINGS & PRIVACY  /  本地" }
)
foreach ($screen in $screens) {
    Tap-UiText $screen.Tab
    Assert-UiText $screen.Marker
}

Tap-UiText "闹钟"
Tap-UiText "+  新建私密闹钟"
Assert-UiText "NEW PRIVATE ALARM"
Invoke-Adb -Arguments @("shell", "input", "swipe", "540", "2050", "540", "450", "500") | Out-Null
Start-Sleep -Milliseconds 500
Assert-UiText "保存并重新调度"
Invoke-Adb -Arguments @("shell", "input", "keyevent", "BACK") | Out-Null
Start-Sleep -Milliseconds 500

$appProcessId = (Invoke-Adb -Arguments @("shell", "pidof", $packageName) | Select-Object -First 1).Trim()
$crashLog = (Invoke-Adb -Arguments @("logcat", "-b", "crash", "-d", "-v", "threadtime")) -join "`n"
if (-not $appProcessId -or $crashLog -match "Process: $([regex]::Escape($packageName))") {
    throw "HushWake smoke test found a crash or missing process`n$crashLog"
}

Write-Output "PASS: onboarding, five primary screens, and alarm editor are alive with PID $appProcessId"
