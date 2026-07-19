$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$WorkspaceRoot = Split-Path -Parent $Root
$Python = Join-Path $WorkspaceRoot "python_envs\pyside6_qrcode\.venv\Scripts\python.exe"
$EntryPoint = Join-Path $Root "scripts\diagnose_ms2011_permissions_readonly.py"
$Name = "MS2011PermissionProbe"
$DistPath = Join-Path $Root "dist"
$WorkPath = Join-Path $Root "build\$Name"
$SpecPath = Join-Path $Root "build\$Name"

if (-not (Test-Path -LiteralPath $Python)) {
    throw "Python environment not found: $Python"
}
if (-not (Test-Path -LiteralPath $EntryPoint)) {
    throw "Probe entry point not found: $EntryPoint"
}

& $Python -c "import pyodbc; print(pyodbc.version)"
if ($LASTEXITCODE -ne 0) {
    throw "pyodbc is not installed in the project environment"
}

Push-Location $Root
try {
    & $Python -m PyInstaller `
        --noconfirm `
        --clean `
        --onedir `
        --console `
        --name $Name `
        --hidden-import pyodbc `
        --distpath $DistPath `
        --workpath $WorkPath `
        --specpath $SpecPath `
        $EntryPoint

    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    $Exe = Join-Path $DistPath "$Name\$Name.exe"
    if (-not (Test-Path -LiteralPath $Exe)) {
        throw "Build finished but Probe exe was not found: $Exe"
    }

    & $Exe --describe-contract
    if ($LASTEXITCODE -ne 0) {
        throw "Probe --describe-contract failed with exit code $LASTEXITCODE"
    }

    Get-Item -LiteralPath $Exe | Select-Object FullName, Length, LastWriteTime | Format-List
    Get-FileHash -LiteralPath $Exe -Algorithm SHA256 | Format-List
}
finally {
    Pop-Location
}
