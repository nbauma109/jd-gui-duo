param(
    [Parameter(Mandatory = $true)]
    [string]$InputArchive,

    [Parameter(Mandatory = $true)]
    [string]$OutputZip
)

$ErrorActionPreference = 'Stop'

$stagingDir = Join-Path $env:RUNNER_TEMP ([guid]::NewGuid().ToString())
New-Item -ItemType Directory -Force -Path $stagingDir | Out-Null

try {
    if (Test-Path $OutputZip) {
        Remove-Item $OutputZip -Force
    }

    tar.exe -xf $InputArchive -C $stagingDir
    Compress-Archive -Path (Join-Path $stagingDir '*') -DestinationPath $OutputZip
}
finally {
    if (Test-Path $stagingDir) {
        Remove-Item $stagingDir -Recurse -Force
    }
}
