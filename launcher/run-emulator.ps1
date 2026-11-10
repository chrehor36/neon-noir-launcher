# Builds the launcher and runs it on the Android TV emulator on this PC.
# Usage (PowerShell, from this folder):  .\run-emulator.ps1
# Starts the emulator if it isn't running, installs the new build and opens Home.
$ErrorActionPreference = 'Stop'
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$adb = "$sdk\platform-tools\adb.exe"
$avd = 'NeonNoir_TV_1080p'
# Gradle 8.9 needs JDK 17 (Android Studio's own Java 25 is too new for it).
$jdk = Get-ChildItem 'C:\Program Files\Microsoft' -Directory -Filter 'jdk-17*' | Select-Object -First 1 -ExpandProperty FullName
if (-not $jdk) { throw 'JDK 17 not found. Install it with: winget install Microsoft.OpenJDK.17' }
$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk

if (-not ((& $adb devices) -match 'emulator-\d+\s+device')) {
    Write-Host 'Starting the emulator...'
    Start-Process "$sdk\emulator\emulator.exe" -ArgumentList '-avd', $avd, '-no-boot-anim'
    & $adb wait-for-device
    while ((& $adb shell getprop sys.boot_completed) -notmatch '1') { Start-Sleep 2 }
}

Write-Host 'Building...'
Push-Location $PSScriptRoot
try {
    & .\gradlew.bat assembleDebug --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw 'Build failed: copy the first error above into the chat.' }
} finally { Pop-Location }

Write-Host 'Installing...'
& $adb install -r "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk" | Out-Null
& $adb shell appops set io.github.chrehor36.neonnoir GET_USAGE_STATS allow
& $adb shell appops set io.github.chrehor36.neonnoir SYSTEM_ALERT_WINDOW allow   # night mode's dim layer
# The emulator's own Google TV home outranks ours; switch it off (emulator only; undo with pm enable).
& $adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx 2>$null | Out-Null
& $adb shell cmd package set-home-activity io.github.chrehor36.neonnoir/.MainActivity | Out-Null
& $adb shell am force-stop io.github.chrehor36.neonnoir
& $adb shell input keyevent KEYCODE_HOME
Write-Host 'Done: Neon Noir Home is on the emulator.'
