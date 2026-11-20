<#
.SYNOPSIS
  Smoke-tests the packaged Windows application: the portable archive (jd-gui-duo.exe) and, optionally, the installer.

.DESCRIPTION
  Portable archive
    * unpacked into a folder whose name contains spaces, started from an unrelated working directory
      (the exe must anchor its classpath to its own folder);
    * the exe carries a product name and a version (no "Unknown application");
    * started on a jar, it shows its main window with the file in the title.
  Installer (when -Installer is given)
    * silent install with the "associatejar" task into a path with spaces;
    * the .jar file association (ProgId, "Open with" entry and open command) is registered, and the registered
      command really opens a jar;
    * a silent install without the task registers no association;
    * the uninstaller removes the files and the registry keys.

.EXAMPLE
  .\test-packaged.ps1 -Archive jd-gui-duo-windows-2.0.118.tar.xz -Fixture packaged-smoke.jar -Installer setup.exe
#>
param(
    [Parameter(Mandatory = $true)] [string] $Archive,
    [Parameter(Mandatory = $true)] [string] $Fixture,
    [string] $Installer,
    [string] $ShotsDir = (Join-Path (Get-Location) 'packaging-shots')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms, System.Drawing

$Archive = (Resolve-Path $Archive).Path
$Fixture = (Resolve-Path $Fixture).Path
New-Item -ItemType Directory -Force -Path $ShotsDir | Out-Null
$work = Join-Path ([System.IO.Path]::GetTempPath()) ("jdgui-packaging-" + [guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $work | Out-Null
$ExpectedTitle = 'packaged-smoke.jar - Java Decompiler'

# The application must not touch the real user configuration
$configDir = Join-Path $work 'config'
New-Item -ItemType Directory -Force -Path $configDir | Out-Null
@'
<?xml version="1.0" encoding="UTF-8"?>
<configuration><gui><lookAndFeel>com.formdev.flatlaf.FlatLightLaf</lookAndFeel></gui></configuration>
'@ | Set-Content -Path (Join-Path $configDir 'jd-gui.cfg') -Encoding utf8
$env:JAVA_TOOL_OPTIONS = "-Djd-gui.cfg=$(Join-Path $configDir 'jd-gui.cfg')"

function Save-Screenshot([string] $name) {
    try {
        $bounds = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
        $bitmap = New-Object System.Drawing.Bitmap $bounds.Width, $bounds.Height
        [System.Drawing.Graphics]::FromImage($bitmap).CopyFromScreen($bounds.Location, [System.Drawing.Point]::Empty, $bounds.Size)
        $bitmap.Save((Join-Path $ShotsDir "$name.png"))
    } catch { Write-Warning "Screenshot failed: $_" }
}

function Wait-MainWindow([string] $title, [int] $seconds = 60) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        $match = Get-Process | Where-Object { $_.MainWindowTitle -like "$title*" } | Select-Object -First 1
        if ($match) { return $match }
        Start-Sleep -Milliseconds 500
    }
    return $null
}

function Stop-Application {
    Get-Process | Where-Object { $_.MainWindowTitle -like '*Java Decompiler*' -or $_.ProcessName -eq 'jd-gui-duo' } |
        ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 1
}

function Assert-Opens([string] $exe, [string] $shot) {
    # started from an unrelated working directory on purpose
    $process = Start-Process -FilePath $exe -ArgumentList ('"{0}"' -f $Fixture) -WorkingDirectory $env:SystemRoot -PassThru
    try {
        $window = Wait-MainWindow $ExpectedTitle
        if (-not $window) {
            Save-Screenshot "$shot-failure"
            throw "No window titled '$ExpectedTitle' appeared for $exe"
        }
        Write-Host "OK: '$($window.MainWindowTitle)' ($exe)"
        Save-Screenshot $shot
    } finally {
        Stop-Application
    }
}

function Test-Portable {
    Write-Host '=== Portable archive'
    $root = Join-Path $work 'folder with spaces\JD GUI'
    New-Item -ItemType Directory -Force -Path $root | Out-Null
    tar.exe -xf $Archive -C $root
    $exe = Join-Path $root 'jd-gui-duo.exe'
    if (-not (Test-Path $exe)) { throw "jd-gui-duo.exe is missing from the archive" }
    if (-not (Test-Path (Join-Path $root 'jre\bin\java.exe'))) { throw "The bundled runtime is missing" }

    $info = (Get-Item $exe).VersionInfo
    Write-Host "ProductName=$($info.ProductName) ProductVersion=$($info.ProductVersion) FileDescription=$($info.FileDescription)"
    if ([string]::IsNullOrWhiteSpace($info.ProductName) -or [string]::IsNullOrWhiteSpace($info.FileDescription)) {
        throw "The exe has no product name / description (Windows would show 'Unknown application')"
    }
    if ($info.ProductVersion -notmatch '^\d+\.\d+') { throw "The exe has no usable version: '$($info.ProductVersion)'" }

    Assert-Opens $exe 'windows-portable'
}

function Get-Association {
    foreach ($hive in 'HKLM', 'HKCU') {
        $command = (Get-ItemProperty -Path "${hive}:\Software\Classes\JDGUIDuo.jar\shell\open\command" -ErrorAction SilentlyContinue).'(default)'
        if ($command) { return @{ Hive = $hive; Command = $command } }
    }
    return $null
}

function Assert-NoAssociation {
    foreach ($hive in 'HKLM', 'HKCU') {
        if (Test-Path "${hive}:\Software\Classes\JDGUIDuo.jar") { throw "Association key still present in $hive" }
        $progIds = Get-ItemProperty -Path "${hive}:\Software\Classes\.jar\OpenWithProgids" -ErrorAction SilentlyContinue
        if ($progIds -and ($progIds.PSObject.Properties.Name -contains 'JDGUIDuo.jar')) { throw "'Open with' entry still present in $hive" }
    }
}

function Install([string] $dir, [string] $tasks) {
    $arguments = @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART', '/SP-', "/DIR=`"$dir`"", "/LOG=`"$work\setup.log`"")
    if ($tasks) { $arguments += "/TASKS=`"$tasks`"" }
    $process = Start-Process -FilePath $Installer -ArgumentList $arguments -Wait -PassThru
    if ($process.ExitCode -ne 0) { Get-Content "$work\setup.log" -ErrorAction SilentlyContinue | Select-Object -Last 40; throw "The installer failed with exit code $($process.ExitCode)" }
}

function Uninstall([string] $dir) {
    $uninstaller = Get-ChildItem -Path $dir -Filter 'unins*.exe' | Select-Object -First 1
    if (-not $uninstaller) { throw "No uninstaller found in $dir" }
    $process = Start-Process -FilePath $uninstaller.FullName -ArgumentList '/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART' -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw "The uninstaller failed with exit code $($process.ExitCode)" }
    $deadline = (Get-Date).AddSeconds(30)
    while ((Test-Path $dir) -and (Get-ChildItem $dir -ErrorAction SilentlyContinue) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 500 }
    if ((Test-Path $dir) -and (Get-ChildItem $dir -ErrorAction SilentlyContinue)) { throw "Files remain in $dir after uninstalling" }
}

function Test-Installer {
    Write-Host '=== Installer with the .jar association task'
    $installDir = Join-Path $work 'Program Files Test\JD GUI Duo'
    Install $installDir 'associatejar'
    $exe = Join-Path $installDir 'jd-gui-duo.exe'
    if (-not (Test-Path $exe)) { throw "The installer did not install jd-gui-duo.exe" }
    Assert-Opens $exe 'windows-installed'

    $association = Get-Association
    if (-not $association) { throw "The .jar association (JDGUIDuo.jar) was not registered" }
    Write-Host "Registered in $($association.Hive): $($association.Command)"
    $progIds = Get-ItemProperty -Path "$($association.Hive):\Software\Classes\.jar\OpenWithProgids"
    if (-not ($progIds.PSObject.Properties.Name -contains 'JDGUIDuo.jar')) { throw "JD-GUI Duo is not listed in 'Open with' for .jar" }
    if ($association.Command -notmatch [regex]::Escape($exe)) { throw "The open command does not point to the installed exe: $($association.Command)" }
    if ($association.Command -notmatch '%1') { throw "The open command does not pass the file (%1): $($association.Command)" }

    # Run the registered command, as Explorer does when opening a jar with the application
    $command = $association.Command.Replace('%1', $Fixture)
    $null = $command -match '^"([^"]+)"\s*(.*)$'
    $process = Start-Process -FilePath $Matches[1] -ArgumentList $Matches[2] -WorkingDirectory $env:SystemRoot -PassThru
    try {
        $window = Wait-MainWindow $ExpectedTitle
        if (-not $window) { Save-Screenshot 'windows-association-failure'; throw "The registered open command did not open the jar" }
        Write-Host "OK: the registered association opens '$($window.MainWindowTitle)'"
    } finally { Stop-Application }

    Uninstall $installDir
    Assert-NoAssociation
    Write-Host 'OK: uninstalled, files and association removed'

    Write-Host '=== Installer without the association task'
    Install $installDir ''
    Assert-NoAssociation
    Assert-Opens (Join-Path $installDir 'jd-gui-duo.exe') 'windows-installed-noassoc'
    Uninstall $installDir
}

try {
    Test-Portable
    if ($Installer) {
        $Installer = (Resolve-Path $Installer).Path
        Test-Installer
    }
    Write-Host 'Packaged Windows application: all checks passed'
} finally {
    Stop-Application
    Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
}
