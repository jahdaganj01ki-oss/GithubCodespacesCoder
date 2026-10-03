# GitCoderA

GitCoderA is a native Android app for managing GitHub repositories and Codespaces, with the Codespace IDE rendered inside the app.

## GitHub Actions builds

- `.github/workflows/android.yml` builds debug and release APKs, runs Android/backend tests and Android lint, packages the OAuth service, and builds its Docker image on GitHub Actions for pushes, pull requests, and manual `workflow_dispatch`.
- Download the `GitCoderA-<commit>` artifact from the completed Actions run to get the APKs.
- The workflow targets Android API 36 and uses JDK 17.
- Define the repository variable `GITHUB_CLIENT_ID` with the public Client ID of the GitHub OAuth App. The same Client ID must be configured on the OAuth backend; it is used by both Device Flow and refresh-token requests. Enable Device Flow on the OAuth App to support sign-in when no backend URL is configured.
- Set `OAUTH_BACKEND_URL` as a repository Actions variable (and as the matching Gradle property/environment variable for direct builds) to use the confidential callback service. When configured, the app opens that backend OAuth flow in a Custom Tab and receives a single-use, PKCE-bound handoff through `gitcodera://oauth/callback`. Otherwise, it uses GitHub Device Flow.
- The OAuth Client Secret stays on the server, never in the APK. Access and refresh tokens are stored in Android Keystore-backed encrypted preferences. `offline_access` is requested; refresh tokens are rotated when the provider returns replacements.
- To sign the release APK in Actions, add all four repository secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. The workflow validates that either all four or none are configured. If absent, Actions produces an unsigned release APK.

The secret `ANDROID_KEYSTORE_BASE64` must contain the base64-encoded keystore file. The keystore itself is never stored in the repository or published as a build artifact.

## Deploy the OAuth callback service

The service source is under [`oauth-server/`](./oauth-server/). Register a GitHub OAuth App with its callback URL set to `https://<your-host>/oauth/callback`, enable Device Flow for the fallback app flow, and deploy one service instance behind an HTTPS reverse proxy. Keep the database volume and `TOKEN_ENCRYPTION_KEY` stable across restarts; do not run multiple instances against the file-backed H2 database.

Required server environment:

- `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET`: OAuth App credentials. The Client ID must match the app build configuration; keep the secret exclusively on the server.
- `PUBLIC_BASE_URL`: the externally reachable HTTPS origin, with no path prefix.
- `TOKEN_ENCRYPTION_KEY`: base64 of exactly 32 cryptographically random bytes (for example, generate once with `openssl rand -base64 32`, then store as a deployment secret).
- `APP_REDIRECT_URI`: optional; defaults to `gitcodera://oauth/callback`.
- `DATABASE_URL`: optional; defaults to a local H2 file. In Docker, the database file is stored in the mounted `/data` volume.
- `PORT`: optional; defaults to `8080`.

The app creates the PKCE verifier and CSRF state on-device. The backend stores short-lived pending transactions, exchanges GitHub's authorization code, encrypts returned tokens at rest, and issues a 120-second single-use handoff bound to the PKCE challenge. The app exchanges that handoff and stores the tokens locally. Use TLS, protect the service secrets and persistent volume, and rotate the GitHub Client Secret / encryption key deliberately; changing the encryption key invalidates pending handoffs.

## App behavior

- Repositories and Codespaces are loaded from the GitHub REST API with pagination and network/API error messages.
- Codespaces can be created, started, stopped, opened in the in-app WebView, and deleted.
- The in-app editor uses the `web_url` returned by GitHub, rejects non-HTTPS and untrusted hosts, recovers from renderer termination, and never receives the GitHub API token from the native app. Android WebView compatibility with the full VS Code web editor is not guaranteed by GitHub; use a supported browser if an extension or editor feature is unavailable.
- Coding-agent keys can be added as GitHub Codespaces user secrets scoped to a selected repository. GitCoderA encrypts them with GitHub's public key, sends the encrypted value directly to GitHub, and does not retain the secret value. GitHub does not return secret contents after saving.
- The API token is stored in Android Keystore-backed encrypted preferences and is removed on sign-out.

The OAuth app needs `repo`, `codespace`, and `read:user` permissions for the implemented features. `offline_access` requests refreshable tokens. Organization administration scope is intentionally not requested. GitHub may require account Codespaces entitlement and organization policy approval.

The Actions artifact contains an unsigned APK until signing secrets are configured. A release build is not a substitute for an end-to-end test against a configured OAuth App, deployed HTTPS callback service, an account with Codespaces entitlement, and a real Codespace. GitHub approval/billing rules and third-party VS Code extensions remain subject to GitHub and extension-provider behavior. GitHub does not guarantee Android WebView support for the full VS Code web editor.