param(
    [string]$Adb = "adb",
    [string]$Apk = "app\build\outputs\apk\debug\app-debug.apk"
)

$ErrorActionPreference = "Stop"
$packageName = "com.hushwake.app"
$componentName = "$packageName/.MainActivity"

& $Adb install -r $Apk | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "APK installation failed"
}

& $Adb logcat -c
& $Adb shell am force-stop $packageName
& $Adb shell am start -W -n $componentName | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "Activity launch command failed"
}

Start-Sleep -Seconds 2
$appProcessId = (& $Adb shell pidof $packageName).Trim()
$crashLog = (& $Adb logcat -b crash -d -v threadtime) -join "`n"
$appCrashed = $crashLog -match "Process: $([regex]::Escape($packageName))"
$activities = (& $Adb shell dumpsys activity activities) -join "`n"
$isForeground = $activities -match "topResumedActivity=.*$([regex]::Escape($componentName))"

if (-not $appProcessId -or $appCrashed -or -not $isForeground) {
    if ($crashLog) {
        Write-Error $crashLog
    }
    throw "HushWake did not remain alive in the foreground after launch"
}

Write-Output "PASS: $componentName is foreground with PID $appProcessId"
