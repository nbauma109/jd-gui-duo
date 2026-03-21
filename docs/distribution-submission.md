# Distribution submission workflows

This repository now focuses on channels that improve discoverability without asking users to add a custom repository first.

## Included workflows

- `.github/workflows/publish-chocolatey.yml`
- `.github/workflows/publish-winget.yml`
- `.github/workflows/prepare-homebrew-cask.yml`

## Windows

### Chocolatey

The Chocolatey workflow publishes directly to the Chocolatey community feed.

Required secret:

- `CHOCO_API_KEY`

Optional variable:

- `CHOCO_SOURCE_URL` if you ever need a non-default Chocolatey push endpoint

### WinGet

The WinGet workflow:

- converts the Windows portable bundle into a `.zip` asset that WinGet can consume
- generates official WinGet manifests
- uploads those manifests as a workflow artifact
- submits them with `wingetcreate submit` when `PAT_TOKEN` is configured

Optional variable:

- `WINGET_PACKAGE_IDENTIFIER` (defaults to `Nbauma109.JDGUIDuo`)

If `PAT_TOKEN` is not configured, the workflow still prepares the manifests for manual submission to `microsoft/winget-pkgs`.

## macOS

The Homebrew workflow now targets the official `homebrew/homebrew-cask` path instead of an upstream tap.

It:

- converts the macOS release asset into a `.zip`
- generates a cask file
- runs `brew style` and `brew audit --new --cask --strict`
- uploads the audited cask as a workflow artifact

Manual PR submission to `homebrew/homebrew-cask` is still required.
