$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:USERPROFILE 'Tools\Android\platform-tools\adb.exe'

if (-not (Test-Path $adb)) {
    throw "ADB not found at $adb"
}

if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem 'C:\Program Files\Microsoft' -Directory -Filter 'jdk-17*' |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if (-not $jdk) {
        throw 'JDK 17 is not installed.'
    }
    $env:JAVA_HOME = $jdk.FullName
}

$env:ANDROID_HOME = Join-Path $env:USERPROFILE 'Android\Sdk'
Push-Location $projectRoot
try {
    & '.\gradlew.bat' --no-daemon assembleDebug
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code $LASTEXITCODE"
    }
    & $adb install -r '.\app\build\outputs\apk\debug\app-debug.apk'
    if ($LASTEXITCODE -ne 0) {
        throw "ADB install failed with exit code $LASTEXITCODE"
    }
    & $adb shell am start -n 'com.cachens.nexusdashboard/.MainActivity'
} finally {
    Pop-Location
}
