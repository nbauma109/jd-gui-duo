param(
    [Parameter(Mandatory = $true)]
    [string]$PackageIdentifier,

    [Parameter(Mandatory = $true)]
    [string]$PackageName,

    [Parameter(Mandatory = $true)]
    [string]$PackageVersion,

    [Parameter(Mandatory = $true)]
    [string]$Publisher,

    [Parameter(Mandatory = $true)]
    [string]$PublisherUrl,

    [Parameter(Mandatory = $true)]
    [string]$PublisherSupportUrl,

    [Parameter(Mandatory = $true)]
    [string]$License,

    [Parameter(Mandatory = $true)]
    [string]$LicenseUrl,

    [Parameter(Mandatory = $true)]
    [string]$ShortDescription,

    [Parameter(Mandatory = $true)]
    [string]$Description,

    [Parameter(Mandatory = $true)]
    [string]$Moniker,

    [Parameter(Mandatory = $true)]
    [string]$ReleaseNotesUrl,

    [Parameter(Mandatory = $true)]
    [string]$InstallerUrl,

    [Parameter(Mandatory = $true)]
    [string]$InstallerSha256,

    [Parameter(Mandatory = $true)]
    [ValidateSet('inno')]
    [string]$InstallerType,

    [Parameter(Mandatory = $true)]
    [string]$OutputRoot
)

$ErrorActionPreference = 'Stop'

$manifestVersion = '1.12.0'

function Write-ManifestFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string[]]$Lines
    )

    [string]::Join([Environment]::NewLine, $Lines) |
        Set-Content -Path $Path -Encoding utf8BOM
}
$parts = $PackageIdentifier.Split('.')
$manifestDir = Join-Path $OutputRoot ('manifests\' + $PackageIdentifier.Substring(0, 1).ToLowerInvariant())
foreach ($part in $parts) {
    $manifestDir = Join-Path $manifestDir $part
}
$manifestDir = Join-Path $manifestDir $PackageVersion
New-Item -ItemType Directory -Force -Path $manifestDir | Out-Null

$versionManifest = @(
    "# Created using repo automation"
    "# yaml-language-server: `$schema=https://aka.ms/winget-manifest.version.$manifestVersion.schema.json"
    ''
    "PackageIdentifier: $PackageIdentifier"
    "PackageVersion: $PackageVersion"
    "DefaultLocale: en-US"
    "ManifestType: version"
    "ManifestVersion: $manifestVersion"
)
Write-ManifestFile -Path (Join-Path $manifestDir "$PackageIdentifier.yaml") -Lines $versionManifest

$defaultLocaleManifest = @(
    "# Created using repo automation"
    "# yaml-language-server: `$schema=https://aka.ms/winget-manifest.defaultLocale.$manifestVersion.schema.json"
    ''
    "PackageIdentifier: $PackageIdentifier"
    "PackageVersion: $PackageVersion"
    "PackageLocale: en-US"
    "Publisher: $Publisher"
    "PublisherUrl: $PublisherUrl"
    "PublisherSupportUrl: $PublisherSupportUrl"
    "PackageName: $PackageName"
    "PackageUrl: $PublisherUrl"
    "License: $License"
    "LicenseUrl: $LicenseUrl"
    "ShortDescription: $ShortDescription"
    "Description: $Description"
    "Moniker: $Moniker"
    "ReleaseNotesUrl: $ReleaseNotesUrl"
    "Tags:"
    "  - java"
    "  - decompiler"
    "  - jd-gui"
    "ManifestType: defaultLocale"
    "ManifestVersion: $manifestVersion"
)
Write-ManifestFile -Path (Join-Path $manifestDir "$PackageIdentifier.locale.en-US.yaml") -Lines $defaultLocaleManifest

$installerManifest = @(
    "# Created using repo automation"
    "# yaml-language-server: `$schema=https://aka.ms/winget-manifest.installer.$manifestVersion.schema.json"
    ''
    "PackageIdentifier: $PackageIdentifier"
    "PackageVersion: $PackageVersion"
    "InstallerType: $InstallerType"
    "InstallModes:"
    "  - interactive"
    "  - silent"
    "  - silentWithProgress"
    "Installers:"
    "  - Architecture: x64"
    "    Scope: machine"
    "    InstallerUrl: $InstallerUrl"
    "    InstallerSha256: $InstallerSha256"
    "    InstallerSwitches:"
    "      Silent: /VERYSILENT /SUPPRESSMSGBOXES /NORESTART /SP-"
    "      SilentWithProgress: /SILENT /SUPPRESSMSGBOXES /NORESTART /SP-"
    "ManifestType: installer"
    "ManifestVersion: $manifestVersion"
)
Write-ManifestFile -Path (Join-Path $manifestDir "$PackageIdentifier.installer.yaml") -Lines $installerManifest
