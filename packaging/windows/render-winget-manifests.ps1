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
    [string]$PortableFile,

    [Parameter(Mandatory = $true)]
    [string]$PortableCommandAlias,

    [Parameter(Mandatory = $true)]
    [string]$OutputRoot
)

$ErrorActionPreference = 'Stop'

$manifestVersion = '1.12.0'
$parts = $PackageIdentifier.Split('.')
$manifestDir = Join-Path $OutputRoot ('manifests\' + $PackageIdentifier.Substring(0, 1).ToLowerInvariant())
foreach ($part in $parts) {
    $manifestDir = Join-Path $manifestDir $part
}
$manifestDir = Join-Path $manifestDir $PackageVersion
New-Item -ItemType Directory -Force -Path $manifestDir | Out-Null

$versionManifest = @(
    "PackageIdentifier: $PackageIdentifier"
    "PackageVersion: $PackageVersion"
    "DefaultLocale: en-US"
    "ManifestType: version"
    "ManifestVersion: $manifestVersion"
)
[string]::Join([Environment]::NewLine, $versionManifest) |
    Set-Content -Path (Join-Path $manifestDir "$PackageIdentifier.yaml")

$defaultLocaleManifest = @(
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
[string]::Join([Environment]::NewLine, $defaultLocaleManifest) |
    Set-Content -Path (Join-Path $manifestDir "$PackageIdentifier.locale.en-US.yaml")

$installerManifest = @(
    "PackageIdentifier: $PackageIdentifier"
    "PackageVersion: $PackageVersion"
    "InstallerType: zip"
    "NestedInstallerType: portable"
    "Installers:"
    "  - Architecture: x64"
    "    InstallerUrl: $InstallerUrl"
    "    InstallerSha256: $InstallerSha256"
    "    NestedInstallerFiles:"
    "      - RelativeFilePath: $PortableFile"
    "        PortableCommandAlias: $PortableCommandAlias"
    "ManifestType: installer"
    "ManifestVersion: $manifestVersion"
)
[string]::Join([Environment]::NewLine, $installerManifest) |
    Set-Content -Path (Join-Path $manifestDir "$PackageIdentifier.installer.yaml")
