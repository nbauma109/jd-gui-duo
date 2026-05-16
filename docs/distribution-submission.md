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

The Chocolatey and WinGet workflows are dispatch-only. The SignPath Windows signing workflow dispatches them after it replaces the release assets with signed Windows binaries, so package checksums are computed from the final signed downloads. Manual dispatches require a release tag and use the tagged sources for packaging logic and release assets.

## Social crosspost secrets

The release pipeline dispatches `.github/workflows/crosspost.yml` after creating a release. Configure the following repository secrets in **GitHub → Settings → Secrets and variables → Actions**.

### Bluesky

- `BLUESKY_IDENTIFIER` — your Bluesky handle, i.e. the last segment of your profile URL.  
  Look at your profile URL:
  - `https://bsky.app/profile/yourname.bsky.social` → set to `yourname.bsky.social`
  - `https://bsky.app/profile/javadecompiler.org` → set to `javadecompiler.org`  
  
  In short: use the part that comes after `https://bsky.app/profile/`, whether it is a `.bsky.social` subdomain or a custom domain.
- `BLUESKY_PASSWORD` (recommended: an app password, not your main account password)

Create an app password in Bluesky:

1. Open Bluesky settings.
2. Go to **Privacy and Security → App Passwords**.
3. Create an app password and store it in `BLUESKY_PASSWORD`.

API posting cost (as of 2026-05):

- Free (`$0`) on official Bluesky infrastructure.
- No paid API tier is required for normal posting.
- Subject to rate limits (for example, write-point quotas) documented here:
  - https://docs.bsky.app/docs/advanced-guides/rate-limits

### Mastodon

- `MASTODON_HOST` (for example `mastodon.social`)
- `MASTODON_ACCESS_TOKEN`

Create a token in your Mastodon instance:

1. Open your account **Preferences → Development**.
2. Create a new application with scopes `write:statuses` and `write:media`.
3. Copy the generated access token to `MASTODON_ACCESS_TOKEN`.

API posting cost:

- Free (`$0`) from the Mastodon software/project perspective.
- There is no central Mastodon API fee.
- Potential indirect costs depend on where you post:
  - public instance: usually free (`$0`) unless that instance has its own policy
  - self-hosted instance: you pay your own server/hosting costs
- Official API docs:
  - https://docs.joinmastodon.org/client/intro/
  - https://docs.joinmastodon.org/methods/

### Dev.to

- `DEVTO_API_KEY`

Get it from Dev.to:

1. Open https://dev.to/settings/extensions
2. In **DEV API Keys**, create or copy your API key.
3. Save it as `DEVTO_API_KEY`.

API posting cost:

- Free (`$0`) for standard API publishing.
- No paid API tier is documented for normal article posting.
- API usage is rate-limited; see official docs:
  - https://developers.forem.com/
  - https://developers.forem.com/api/v0

### Hacker News

- `HN_USERNAME` — the username for a dedicated posting-only Hacker News account used by this workflow
- `HN_PASSWORD` — the password for that dedicated posting-only Hacker News account

Each release is submitted as a **Show HN** post with the release URL. Hacker News has no API key or scoped app-password system, so the action authenticates with full account credentials directly. Do not use a maintainer's primary personal Hacker News account; create a separate posting-only account and store only that account's credentials in `HN_USERNAME` and `HN_PASSWORD`.

API posting cost:

- Free (`$0`). Hacker News has no paid API or posting tier.

### GitHub token for downstream workflow dispatch

- `PAT_TOKEN`

This token is used by `release.yml` to dispatch downstream workflows (`rewrite-release-notes.yml` and `crosspost.yml`) after release creation.

1. Create a personal access token for a maintainer account.
2. Ensure it can run workflows in this repository (for classic tokens, include `repo` and `workflow` scopes).
3. Save it as `PAT_TOKEN` in repository Actions secrets.

