$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$cache = Join-Path $projectRoot '.local\novi-sad-cache'
$manifestPath = Join-Path $cache 'manifest.json'
$adb = Join-Path $env:USERPROFILE 'Tools\Android\platform-tools\adb.exe'
$remoteRoot = '/sdcard/NexusWallDashboard'
$remoteCache = "$remoteRoot/city-cache"

if (-not (Test-Path $adb)) {
    throw "ADB not found at $adb"
}
if (-not (Test-Path $manifestPath)) {
    throw "Photo manifest not found at $manifestPath"
}

$manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
if ($manifest.photos.Count -ne 100) {
    throw "Expected exactly 100 manifest entries; found $($manifest.photos.Count)"
}
$manifestNames = @($manifest.photos | ForEach-Object { $_.filename })
if (@($manifestNames | Select-Object -Unique).Count -ne 100) {
    throw 'The photo manifest contains duplicate filenames.'
}
for ($i = 1; $i -le 100; $i++) {
    $expected = 'photo_{0:D3}.jpg' -f $i
    if ($manifestNames -notcontains $expected) {
        throw "The photo manifest is missing $expected"
    }
}

$files = @(Get-ChildItem $cache -File -Filter 'photo_*.jpg')
if ($files.Count -ne 100) {
    throw "Expected exactly 100 JPEG files; found $($files.Count)"
}

foreach ($photo in $manifest.photos) {
    $path = Join-Path $cache $photo.filename
    if (-not (Test-Path $path)) {
        throw "Missing photo $($photo.filename)"
    }
    $actualHash = (Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $photo.sha256.ToLowerInvariant()) {
        throw "SHA-256 mismatch for $($photo.filename)"
    }
}

& $adb devices
if ($LASTEXITCODE -ne 0) {
    throw 'Could not query connected Android devices.'
}

& $adb shell rm -rf $remoteCache
& $adb shell mkdir -p $remoteCache
& $adb push (Join-Path $cache '.') $remoteCache
if ($LASTEXITCODE -ne 0) {
    throw 'Could not stage the Novi Sad photo cache on the tablet.'
}

& $adb logcat -c
& $adb shell am force-stop com.cachens.nexusdashboard
& $adb shell am start -n com.cachens.nexusdashboard/.MainActivity
Start-Sleep -Seconds 30

$log = & $adb logcat -d -s NexusDashboard:V '*:S'
if (($log -join "`n") -notmatch 'Imported 100 Novi Sad photos') {
    $log | Write-Host
    throw 'The app did not confirm importing all 100 Novi Sad photos.'
}

Write-Host 'Imported 100 Novi Sad photos into app-private storage.'
