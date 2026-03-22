param(
    [Parameter(Mandatory = $true)]
    [string]$InputArchive,

    [Parameter(Mandatory = $true)]
    [string]$OutputInstaller,

    [Parameter(Mandatory = $true)]
    [string]$AppVersion
)

$ErrorActionPreference = 'Stop'

function Get-IsccPath {
    $command = Get-Command ISCC.exe -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }

    $commonPaths = @(
        "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
        "${env:ProgramFiles}\Inno Setup 6\ISCC.exe"
    )

    foreach ($path in $commonPaths) {
        if (-not [string]::IsNullOrWhiteSpace($path) -and (Test-Path $path)) {
            return $path
        }
    }

    throw "ISCC.exe was not found. Install Inno Setup before building the Windows installer."
}

function Escape-InnoValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    return $Value
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$templatePath = Join-Path $PSScriptRoot 'jd-gui-duo.iss.in'
$iconPath = Join-Path $repoRoot 'src\launch4j\resources\images\jd-gui.ico'
$stagingDir = Join-Path $env:RUNNER_TEMP ([guid]::NewGuid().ToString())
$scriptPath = Join-Path $stagingDir 'jd-gui-duo.iss'

New-Item -ItemType Directory -Force -Path $stagingDir | Out-Null

try {
    if (Test-Path $OutputInstaller) {
        Remove-Item $OutputInstaller -Force
    }

    tar.exe -xf $InputArchive -C $stagingDir

    $appExecutableName = "jd-gui-duo-$AppVersion.exe"
    $appExecutablePath = Join-Path $stagingDir $appExecutableName
    if (-not (Test-Path $appExecutablePath)) {
        throw "Expected application executable '$appExecutableName' was not found in the extracted Windows bundle."
    }

    if (-not (Test-Path $iconPath)) {
        throw "Expected installer icon was not found at '$iconPath'."
    }

    $outputDirectory = Split-Path -Parent $OutputInstaller
    $outputBaseName = [System.IO.Path]::GetFileNameWithoutExtension($OutputInstaller)
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

    $script = Get-Content -Path $templatePath -Raw
    $script = $script.Replace('__APP_VERSION__', $AppVersion)
    $script = $script.Replace('__APP_EXE__', $appExecutableName)
    $script = $script.Replace('__SOURCE_DIR__', (Escape-InnoValue ((Resolve-Path $stagingDir).Path)))
    $script = $script.Replace('__OUTPUT_DIR__', (Escape-InnoValue ((Resolve-Path $outputDirectory).Path)))
    $script = $script.Replace('__OUTPUT_BASENAME__', $outputBaseName)
    $script = $script.Replace('__SETUP_ICON_FILE__', (Escape-InnoValue ((Resolve-Path $iconPath).Path)))
    Set-Content -Path $scriptPath -Value $script -Encoding utf8

    $isccPath = Get-IsccPath
    & $isccPath "/Qp" $scriptPath
    if ($LASTEXITCODE -ne 0) {
        throw "Inno Setup failed with exit code $LASTEXITCODE."
    }
}
finally {
    if (Test-Path $stagingDir) {
        Remove-Item $stagingDir -Recurse -Force
    }
}
