$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$envRoot = "E:\AndroidBuildEnv"
$androidSdk = Join-Path $envRoot "android-sdk"
$gradleBat = Join-Path $envRoot "gradle\gradle-8.11\bin\gradle.bat"
$gradleUserHome = Join-Path $envRoot ".gradle-cache"

if (-not (Test-Path -LiteralPath $androidSdk)) {
    throw "Android SDK not found: $androidSdk"
}
if (-not (Test-Path -LiteralPath $gradleBat)) {
    throw "Gradle not found: $gradleBat"
}

New-Item -ItemType Directory -Force -Path $gradleUserHome | Out-Null

$env:ANDROID_HOME = $androidSdk
$env:ANDROID_SDK_ROOT = $androidSdk
$env:GRADLE_USER_HOME = $gradleUserHome
$env:JAVA_HOME = "E:\AndroidBuildEnv\jdk\jdk-17.0.19+10"

Push-Location $projectRoot
try {
    & $gradleBat --no-daemon :app:assembleDebug
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
finally {
    Pop-Location
}
