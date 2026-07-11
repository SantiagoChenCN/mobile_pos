$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$WorkspaceRoot = Split-Path -Parent $Root
$Python = Join-Path $WorkspaceRoot "python_envs\pyside6_qrcode\.venv\Scripts\python.exe"

if (-not (Test-Path -LiteralPath $Python)) {
    throw "Python environment not found: $Python"
}

Push-Location $Root
try {
    & $Python -m PyInstaller `
        --noconfirm `
        --clean `
        --onedir `
        --windowed `
        --name MobilePosSync `
        --paths "$Root\src" `
        "$Root\src\app.py"

    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    $Exe = Join-Path $Root "dist\MobilePosSync\MobilePosSync.exe"
    if (-not (Test-Path -LiteralPath $Exe)) {
        throw "Build finished but exe was not found: $Exe"
    }

    Get-Item -LiteralPath $Exe | Select-Object FullName, Length, LastWriteTime | Format-List
}
finally {
    Pop-Location
}
