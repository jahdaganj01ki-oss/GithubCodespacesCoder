# GitCoderA

GitCoderA is a native Android app for managing GitHub repositories and Codespaces, with the Codespace IDE rendered inside the app.

## GitHub Actions builds

- `.github/workflows/android.yml` builds debug and release APKs, runs unit tests, and runs Android lint for pushes, pull requests, and manual `workflow_dispatch`.
- Download the `GitCoderA-<commit>` artifact from the completed Actions run to get the APKs.
- The workflow targets Android API 36 and uses JDK 17.
- Define the repository variable `GITHUB_CLIENT_ID` with the public Client ID of a GitHub OAuth App that has Device Flow enabled. Without it, the APK builds but GitHub sign-in is unavailable.
- Authentication uses GitHub Device Flow in a Custom Tab, with access and refresh tokens stored in Android Keystore-backed encrypted preferences. `offline_access` is requested so expiring OAuth tokens can be refreshed. A native APK cannot keep an OAuth Client Secret confidential, so GitCoderA does not embed one or offer a nonfunctional backend-OAuth switch.
- To sign the release APK in Actions, add all four repository secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. The workflow validates that either all four or none are configured. If absent, Actions produces an unsigned release APK.

The secret `ANDROID_KEYSTORE_BASE64` must contain the base64-encoded keystore file. The keystore itself is never stored in the repository or published as a build artifact.

## App behavior

- Repositories and Codespaces are loaded from the GitHub REST API with pagination and network/API error messages.
- Codespaces can be created, started, stopped, opened in the in-app WebView, and deleted.
- The in-app editor uses the `web_url` returned by GitHub, rejects non-HTTPS and untrusted hosts, and never receives the GitHub API token from the native app. Android WebView compatibility with the full VS Code web editor is not guaranteed by GitHub; use a supported browser if an extension or editor feature is unavailable.
- Coding-agent keys can be added as GitHub Codespaces user secrets scoped to a selected repository. GitCoderA encrypts them with GitHub's public key, sends the encrypted value directly to GitHub, and does not retain the secret value. GitHub does not return secret contents after saving.
- The API token is stored in Android Keystore-backed encrypted preferences and is removed on sign-out.

The OAuth app needs `repo`, `codespace`, and `read:user` permissions for the implemented features. `offline_access` requests refreshable tokens. Organization administration scope is intentionally not requested. GitHub may require account Codespaces entitlement and organization policy approval.

The release build is not a substitute for an end-to-end test against a configured OAuth App, an account with Codespaces entitlement, and a real Codespace. GitHub approval/billing rules and third-party VS Code extensions remain subject to GitHub and extension-provider behavior. GitHub does not guarantee Android WebView support for the full VS Code web editor.