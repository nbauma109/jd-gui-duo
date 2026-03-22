param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [string]$PackageIdentifier = 'Nbauma109.JDGUIDuo',

    [string]$PackageName = 'JD-GUI Duo',

    [string]$Publisher = 'Nicolas Baumann',

    [string]$Moniker = 'jd-gui-duo',

    [string]$ShortDescription = 'A 2-in-1 Java decompiler based on JD-Core v0 and v1',

    [string]$Repository = 'nbauma109/jd-gui-duo',

    [string]$InstallerPath,

    [string]$InstallerUrl,

    [string]$OutputRoot = (Join-Path $PSScriptRoot '..\..\dist\winget')
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$renderScriptPath = Join-Path $repoRoot 'packaging\windows\render-winget-manifests.ps1'

if ([string]::IsNullOrWhiteSpace($InstallerPath)) {
    $InstallerPath = Join-Path $repoRoot "assembler\target\jd-gui-duo-windows-$Version-setup.exe"
}

if ([string]::IsNullOrWhiteSpace($InstallerUrl)) {
    $InstallerUrl = "https://github.com/$Repository/releases/download/$Version/jd-gui-duo-windows-$Version-setup.exe"
}

if (-not (Test-Path $InstallerPath)) {
    throw "Installer path '$InstallerPath' does not exist."
}

$resolvedInstallerPath = (Resolve-Path $InstallerPath).Path
$resolvedOutputRoot = [System.IO.Path]::GetFullPath($OutputRoot)
New-Item -ItemType Directory -Force -Path $resolvedOutputRoot | Out-Null

$publisherUrl = "https://github.com/$Repository"
$installerSha256 = (Get-FileHash -Algorithm SHA256 $resolvedInstallerPath).Hash.ToUpperInvariant()

& $renderScriptPath `
    -PackageIdentifier $PackageIdentifier `
    -PackageName $PackageName `
    -PackageVersion $Version `
    -Publisher $Publisher `
    -PublisherUrl $publisherUrl `
    -PublisherSupportUrl "$publisherUrl/issues" `
    -License 'GPL-3.0-only' `
    -LicenseUrl "$publisherUrl/blob/$Version/LICENSE" `
    -ShortDescription $ShortDescription `
    -Description $ShortDescription `
    -Moniker $Moniker `
    -ReleaseNotesUrl "$publisherUrl/releases/tag/$Version" `
    -InstallerUrl $InstallerUrl `
    -InstallerSha256 $installerSha256 `
    -InstallerType 'inno' `
    -OutputRoot $resolvedOutputRoot

$packagePath = $PackageIdentifier -replace '\.', '\'
$manifestPath = Join-Path $resolvedOutputRoot ("manifests\{0}\{1}\$Version" -f $PackageIdentifier.Substring(0, 1).ToLowerInvariant(), $packagePath)

Write-Host "Manifest path: $manifestPath"
