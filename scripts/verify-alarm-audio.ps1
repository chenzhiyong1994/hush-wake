param(
    [double]$MaxSilenceSeconds = 1.5
)

$ErrorActionPreference = "Stop"
$rawDirectory = Join-Path $PSScriptRoot "..\app\src\main\res\raw"
$files = @(Get-ChildItem -LiteralPath $rawDirectory -Filter "alarm_*.ogg" | Sort-Object Name)

if ($files.Count -ne 6) {
    throw "Expected 6 alarm recordings, found $($files.Count)."
}

$issues = @()
foreach ($file in $files) {
    $duration = [double](& ffprobe -v error -show_entries format=duration `
        -of default=noprint_wrappers=1:nokey=1 $file.FullName)
    if ($LASTEXITCODE -ne 0 -or $duration -le 0) {
        $issues += "$($file.Name): cannot be decoded"
        continue
    }

    $analysis = & ffmpeg -hide_banner -i $file.FullName `
        -af "silencedetect=noise=-45dB:d=0.25" -f null NUL 2>&1
    if ($LASTEXITCODE -ne 0) {
        $issues += "$($file.Name): ffmpeg decode failed"
        continue
    }

    $longestSilence = 0.0
    foreach ($line in $analysis) {
        if ($line -match "silence_duration: ([0-9.]+)") {
            $longestSilence = [Math]::Max($longestSilence, [double]$Matches[1])
        }
    }
    Write-Output ("{0}: {1:N2}s, longest near-silence {2:N2}s" -f `
        $file.Name, $duration, $longestSilence)
    if ($longestSilence -gt $MaxSilenceSeconds) {
        $issues += ("{0}: near-silence {1:N2}s exceeds {2:N2}s" -f `
            $file.Name, $longestSilence, $MaxSilenceSeconds)
    }
}

if ($issues.Count -gt 0) {
    throw ($issues -join [Environment]::NewLine)
}

Write-Output "Alarm audio verification passed."
