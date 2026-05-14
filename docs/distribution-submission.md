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

API posting cost (as of 2026-05):

- Not fully free for normal automation use.
- X moved to paid API access for most production usage.
- For non-legacy developers, posting is typically billed pay-per-use (commonly reported around `$0.015` per post without URL and much higher for URL posts), and pricing can change.
- Legacy fixed plans (`Basic`, `Pro`) are generally not open to new signups.
- Check the official pricing page and your Developer Console before enabling automation:
  - https://developer.x.com/en/products/x-api
  - https://developer.x.com/docs/twitter-api/pricing

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
2. Create a new application with posting scope (`write:statuses`).
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

1. Open https://dev.to/settings/account
2. Create/copy an API key in the Dev.to API key section.
3. Save it as `DEVTO_API_KEY`.

API posting cost:

- Free (`$0`) for standard API publishing.
- No paid API tier is documented for normal article posting.
- API usage is rate-limited; see official docs:
  - https://developers.forem.com/
  - https://developers.forem.com/api/v0

### GitHub token for downstream workflow dispatch

- `PAT_TOKEN`

This token is used by `release.yml` to dispatch downstream workflows (`rewrite-release-notes.yml` and `crosspost.yml`) after release creation.

1. Create a personal access token for a maintainer account.
2. Ensure it can run workflows in this repository (for classic tokens, include `repo` and `workflow` scopes).
3. Save it as `PAT_TOKEN` in repository Actions secrets.
