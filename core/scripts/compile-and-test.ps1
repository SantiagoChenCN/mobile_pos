$ErrorActionPreference = "Stop"

$coreRoot = Split-Path -Parent $PSScriptRoot
$buildDir = Join-Path ([System.IO.Path]::GetTempPath()) ("mobilepos-core-test-" + [Guid]::NewGuid().ToString("N"))

New-Item -ItemType Directory -Force -Path $buildDir | Out-Null

$mainSources = Get-ChildItem -Recurse -Filter *.java -Path (Join-Path $coreRoot "src\main\java") | ForEach-Object { $_.FullName }
$testSources = Get-ChildItem -Recurse -Filter *.java -Path (Join-Path $coreRoot "src\test\java") | ForEach-Object { $_.FullName }
$sources = @($mainSources) + @($testSources)

javac -encoding UTF-8 -d $buildDir $sources
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

java -cp $buildDir com.espsa.mobilepos.core.CoreSmokeTest
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
