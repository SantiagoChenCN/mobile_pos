$ErrorActionPreference = "Stop"

$envRoot = "E:\AndroidBuildEnv"
$downloads = Join-Path $envRoot "downloads"
$androidSdk = Join-Path $envRoot "android-sdk"
$gradleRoot = Join-Path $envRoot "gradle"
$gradleVersion = "8.11"

$cmdlineToolsUrl = "https://dl.google.com/android/repository/commandlinetools-win-14742923_latest.zip"
$cmdlineToolsSha256Prefix = "16b3f45ddb3d85ea6bbe6a1c0b47146daf0db450"
$gradleUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"

$cmdlineZip = Join-Path $downloads "commandlinetools-win-14742923_latest.zip"
$gradleZip = Join-Path $downloads "gradle-$gradleVersion-bin.zip"
$cmdlineExtract = Join-Path $envRoot "cmdline-tools-extract"
$cmdlineLatest = Join-Path $androidSdk "cmdline-tools\latest"
$gradleHome = Join-Path $gradleRoot "gradle-$gradleVersion"

New-Item -ItemType Directory -Force -Path $downloads, $androidSdk, $gradleRoot | Out-Null

function Download-IfMissing($url, $path) {
    if (Test-Path -LiteralPath $path) {
        Write-Host "Already downloaded: $path"
        return
    }
    Write-Host "Downloading $url"
    Invoke-WebRequest -Uri $url -OutFile $path
}

Download-IfMissing $cmdlineToolsUrl $cmdlineZip
Download-IfMissing $gradleUrl $gradleZip

$actualCmdHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $cmdlineZip).Hash.ToLowerInvariant()
if (-not $actualCmdHash.StartsWith($cmdlineToolsSha256Prefix)) {
    Write-Warning "Android command line tools SHA256 did not match the short checksum shown by the web page extraction. Actual full SHA256: $actualCmdHash"
}

if (-not (Test-Path -LiteralPath $cmdlineLatest)) {
    Remove-Item -Recurse -Force -LiteralPath $cmdlineExtract -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $cmdlineExtract | Out-Null
    Expand-Archive -LiteralPath $cmdlineZip -DestinationPath $cmdlineExtract -Force
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $cmdlineLatest) | Out-Null
    Move-Item -LiteralPath (Join-Path $cmdlineExtract "cmdline-tools") -Destination $cmdlineLatest
    Remove-Item -Recurse -Force -LiteralPath $cmdlineExtract
}

if (-not (Test-Path -LiteralPath $gradleHome)) {
    Expand-Archive -LiteralPath $gradleZip -DestinationPath $gradleRoot -Force
}

$sdkManager = Join-Path $cmdlineLatest "bin\sdkmanager.bat"
$gradleBat = Join-Path $gradleHome "bin\gradle.bat"

if (-not (Test-Path -LiteralPath $sdkManager)) {
    throw "sdkmanager not found: $sdkManager"
}
if (-not (Test-Path -LiteralPath $gradleBat)) {
    throw "gradle not found: $gradleBat"
}

$env:ANDROID_HOME = $androidSdk
$env:ANDROID_SDK_ROOT = $androidSdk

Write-Host "Accepting Android SDK licenses"
$yesLines = 1..200 | ForEach-Object { "y" }
$yesLines | & $sdkManager --sdk_root=$androidSdk --licenses

Write-Host "Installing Android SDK packages"
& $sdkManager --sdk_root=$androidSdk "platform-tools" "platforms;android-35" "build-tools;35.0.0"

Write-Host ""
Write-Host "Android build environment ready"
Write-Host "ENV_ROOT=$envRoot"
Write-Host "ANDROID_HOME=$androidSdk"
Write-Host "GRADLE_HOME=$gradleHome"
Write-Host "GRADLE_BAT=$gradleBat"
