param(
    [Parameter(Mandatory = $true)]
    [string]$Root,

    [Parameter(Mandatory = $true)]
    [string]$SignedExecutable
)

$ErrorActionPreference = 'Stop'

$targets = @(Get-ChildItem $Root -Recurse -File -Filter 'jd-gui-duo.exe')
if ($targets.Count -eq 0) {
    throw "No jd-gui-duo.exe was found under $Root."
}

foreach ($target in $targets) {
    Copy-Item $SignedExecutable $target.FullName -Force
}
