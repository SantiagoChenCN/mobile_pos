$ErrorActionPreference = "Stop"

$envRoot = "E:\AndroidBuildEnv"
$downloads = Join-Path $envRoot "downloads"
$jdkRoot = Join-Path $envRoot "jdk"
$jdkZip = Join-Path $downloads "temurin-jdk17-windows-x64.zip"
$jdkUrl = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"

New-Item -ItemType Directory -Force -Path $downloads, $jdkRoot | Out-Null

if (-not (Test-Path -LiteralPath $jdkZip)) {
    Write-Host "Downloading Temurin JDK 17"
    Invoke-WebRequest -Uri $jdkUrl -OutFile $jdkZip
}
else {
    Write-Host "Already downloaded: $jdkZip"
}

if (-not (Get-ChildItem -LiteralPath $jdkRoot -Directory -Filter "jdk-17*" -ErrorAction SilentlyContinue)) {
    Expand-Archive -LiteralPath $jdkZip -DestinationPath $jdkRoot -Force
}

$jdkHome = (Get-ChildItem -LiteralPath $jdkRoot -Directory -Filter "jdk-17*" | Select-Object -First 1).FullName
if (-not $jdkHome) {
    throw "JDK 17 install directory not found under $jdkRoot"
}

& (Join-Path $jdkHome "bin\java.exe") -version
& (Join-Path $jdkHome "bin\javac.exe") -version

Write-Host "JDK17_HOME=$jdkHome"

