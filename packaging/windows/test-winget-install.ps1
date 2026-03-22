param(
    [Parameter(Mandatory = $true)]
    [string]$ManifestPath,

    [string]$Version,

    [string]$WorkingDirectory = (Join-Path $PSScriptRoot '..\..\dist\winget-local-test')
)

$ErrorActionPreference = 'Stop'

function Write-NewLogLines {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Label,

        [Parameter(Mandatory = $true)]
        [ref]$LastCount
    )

    if (-not (Test-Path $Path)) {
        return
    }

    $lines = Get-Content -Path $Path
    if ($null -eq $lines) {
        return
    }

    if ($lines -is [string]) {
        $lines = @($lines)
    }

    if ($lines.Count -le $LastCount.Value) {
        return
    }

    Write-Host "===== $Label ====="
    $lines[$LastCount.Value..($lines.Count - 1)] | ForEach-Object { Write-Host $_ }
    Write-Host "===== end $Label ====="
    $LastCount.Value = $lines.Count
}

if (-not (Test-Path $ManifestPath)) {
    throw "Manifest path '$ManifestPath' does not exist."
}

$resolvedManifestPath = (Resolve-Path $ManifestPath).Path
New-Item -ItemType Directory -Force -Path $WorkingDirectory | Out-Null
$resolvedWorkingDirectory = (Resolve-Path $WorkingDirectory).Path

$wingetLogPath = Join-Path $resolvedWorkingDirectory 'winget-install.log'
$wingetStdoutPath = Join-Path $resolvedWorkingDirectory 'winget-install.stdout.log'
$wingetStderrPath = Join-Path $resolvedWorkingDirectory 'winget-install.stderr.log'
$innoLogPath = Join-Path $resolvedWorkingDirectory 'inno-install.log'
$runnerScriptPath = Join-Path $resolvedWorkingDirectory 'run-winget.ps1'

winget settings --enable LocalManifestFiles
winget settings --enable LocalArchiveMalwareScanOverride

$overrideArgs = "/VERYSILENT /SUPPRESSMSGBOXES /NORESTART /SP- /LOG=`"$innoLogPath`""
$wingetPath = (Get-Command winget).Source
$wingetArgs = @(
    'install'
    '--manifest'
    $resolvedManifestPath
    '--scope'
    'machine'
    '--silent'
    '--accept-package-agreements'
    '--accept-source-agreements'
    '--ignore-local-archive-malware-scan'
    '--override'
    $overrideArgs
    '--log'
    $wingetLogPath
    '--verbose-logs'
)

$quotedWingetPath = $wingetPath.Replace("'", "''")
$argumentLiteralLines = $wingetArgs | ForEach-Object {
    "  '" + ($_.Replace("'", "''")) + "'"
}
$runnerScriptLines = @(
    '$ErrorActionPreference = ''Stop'''
    '$arguments = @('
) + $argumentLiteralLines + @(
    ')'
    "& '$quotedWingetPath' @arguments"
    'exit $LASTEXITCODE'
)
[string]::Join([Environment]::NewLine, $runnerScriptLines) |
    Set-Content -Path $runnerScriptPath -Encoding utf8

$stdoutCount = 0
$stderrCount = 0
$wingetLogCount = 0
$innoLogCount = 0

$runnerPwsh = (Get-Command pwsh).Source
$wingetProcess = Start-Process `
    -FilePath $runnerPwsh `
    -ArgumentList @('-File', $runnerScriptPath) `
    -PassThru `
    -RedirectStandardOutput $wingetStdoutPath `
    -RedirectStandardError $wingetStderrPath

while (-not $wingetProcess.HasExited) {
    Start-Sleep -Seconds 2
    Write-NewLogLines -Path $wingetStdoutPath -Label 'winget stdout' -LastCount ([ref]$stdoutCount)
    Write-NewLogLines -Path $wingetStderrPath -Label 'winget stderr' -LastCount ([ref]$stderrCount)
    Write-NewLogLines -Path $wingetLogPath -Label 'winget install log' -LastCount ([ref]$wingetLogCount)
    Write-NewLogLines -Path $innoLogPath -Label 'inno install log' -LastCount ([ref]$innoLogCount)
}

Write-NewLogLines -Path $wingetStdoutPath -Label 'winget stdout' -LastCount ([ref]$stdoutCount)
Write-NewLogLines -Path $wingetStderrPath -Label 'winget stderr' -LastCount ([ref]$stderrCount)
Write-NewLogLines -Path $wingetLogPath -Label 'winget install log' -LastCount ([ref]$wingetLogCount)
Write-NewLogLines -Path $innoLogPath -Label 'inno install log' -LastCount ([ref]$innoLogCount)

if ($wingetProcess.ExitCode -ne 0) {
    throw "winget install exited with code $($wingetProcess.ExitCode)."
}

if (-not [string]::IsNullOrWhiteSpace($Version)) {
    $programFilesRoot = if ([string]::IsNullOrWhiteSpace($env:ProgramW6432)) { $env:ProgramFiles } else { $env:ProgramW6432 }
    $installedExecutable = Join-Path $programFilesRoot "JD-GUI Duo\jd-gui-duo-$Version.exe"
    if (-not (Test-Path $installedExecutable)) {
        throw "The installed application executable was not found at '$installedExecutable'."
    }

    $process = Start-Process -FilePath $installedExecutable -PassThru
    Start-Sleep -Seconds 10

    if ($process.HasExited) {
        throw "The installed application exited immediately with code $($process.ExitCode)."
    }

    Stop-Process -Id $process.Id -Force
}

Write-Host "Local WinGet install test completed successfully."
