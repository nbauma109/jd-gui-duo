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
- uploads those manifests as a workflow artifact
- submits them with `wingetcreate submit` when `PAT_TOKEN` is configured

Optional variable:

- `WINGET_PACKAGE_IDENTIFIER` (defaults to `Nbauma109.JDGUIDuo`)

If `PAT_TOKEN` is not configured, the workflow still prepares the manifests for manual submission to `microsoft/winget-pkgs`.

On `workflow_dispatch`, the workflow uses the branch selected in the GitHub UI for packaging logic and the provided tag for release assets. On `release` events, it uses the tagged sources.

## Social crosspost secrets

The release pipeline dispatches `.github/workflows/crosspost.yml` after creating a release. Configure the following repository secrets in **GitHub → Settings → Secrets and variables → Actions**.

### X (Twitter)

- `TWITTER_API_CONSUMER_KEY`
- `TWITTER_API_CONSUMER_SECRET`
- `TWITTER_ACCESS_TOKEN_KEY`
- `TWITTER_ACCESS_TOKEN_SECRET`

Get these from the X Developer Portal:

1. Go to https://developer.x.com/
2. Open your project/app and create or view API keys and tokens.
3. Copy the API key/secret and the access token/secret into the matching GitHub secrets.

### Bluesky

- `BLUESKY_IDENTIFIER` (usually your handle, for example `yourname.bsky.social`)
- `BLUESKY_PASSWORD` (recommended: an app password, not your main account password)

Create an app password in Bluesky:

1. Open Bluesky settings.
2. Go to **Privacy and Security → App Passwords**.
3. Create an app password and store it in `BLUESKY_PASSWORD`.

### Mastodon

- `MASTODON_HOST` (for example `mastodon.social`)
- `MASTODON_ACCESS_TOKEN`

Create a token in your Mastodon instance:

1. Open your account **Preferences → Development**.
2. Create a new application with posting scope (`write:statuses`).
3. Copy the generated access token to `MASTODON_ACCESS_TOKEN`.

### Dev.to

- `DEVTO_API_KEY`

Get it from Dev.to:

1. Open https://dev.to/settings/account
2. Create/copy an API key in the Dev.to API key section.
3. Save it as `DEVTO_API_KEY`.

### GitHub token for downstream workflow dispatch

- `PAT_TOKEN`

This token is used by `release.yml` to dispatch downstream workflows (`rewrite-release-notes.yml` and `crosspost.yml`) after release creation.

1. Create a personal access token for a maintainer account.
2. Ensure it can run workflows in this repository (for classic tokens, include `repo` and `workflow` scopes).
3. Save it as `PAT_TOKEN` in repository Actions secrets.
