# Distribution submission workflows

This repository now focuses on channels that improve discoverability without asking users to add a custom repository first.

## Included workflows

- `.github/workflows/publish-chocolatey.yml`
- `.github/workflows/publish-winget.yml`

## Windows

### Chocolatey

The Chocolatey workflow publishes directly to the Chocolatey community feed.

Required secret:

- `CHOCO_API_KEY`

Optional variable:

- `CHOCO_SOURCE_URL` if you ever need a non-default Chocolatey push endpoint

### WinGet

The WinGet workflow:

- uses a Windows installer `.exe` asset built from the existing Windows application bundle
- generates official WinGet manifests using the 1.12 schema
- validates them with `winget validate --manifest <path>`
- tests them with `winget install --manifest <path>`
- uploads those manifests as a workflow artifact
- submits them with `wingetcreate submit` when `PAT_TOKEN` is configured

Optional variable:

- `WINGET_PACKAGE_IDENTIFIER` (defaults to `Nbauma109.JDGUIDuo`)

If `PAT_TOKEN` is not configured, the workflow still prepares the manifests for manual submission to `microsoft/winget-pkgs`.

On `workflow_dispatch`, the workflow uses the branch selected in the GitHub UI for packaging logic and the provided tag for release assets. On `release` events, it uses the tagged sources.
