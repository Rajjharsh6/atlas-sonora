# ATLAS SONORA

Personal Android music-discovery app. GitHub Actions builds the APK; no Android Studio workflow is needed.

## Included
- Kotlin + Jetpack Compose Android app.
- AI music curator powered by Grok (xAI), called directly from the phone (no server).
- Mood selection and custom natural-language music requests.
- Local liked/disliked taste memory, sent with future recommendations.
- Playback through the Spotify app (App Remote SDK, Premium).
- GitHub Actions APK build workflow.

## Spotify playback (Premium)
Tapping a pick plays it through the **Spotify App Remote SDK**, which controls the official Spotify app (installed, logged in, Premium). Audio is never extracted or re-streamed. The app resolves each AI pick to a real `spotify:track:` URI via the Spotify Web API (unmatched, likely invented songs are dropped).

### One-time Spotify setup
1. Create an app at https://developer.spotify.com/dashboard and enable **Android SDK**.
2. Add redirect URI `atlassonora://callback`.
3. Add Android package `com.atlas.sonora` with SHA-1 `52:A7:0A:5F:44:CF:4A:43:1E:C5:D5:8E:49:1D:AD:87:31:3D:58:0D` (the fixed key in `keystore/debug.keystore`, so every CI build matches).
4. Add your Spotify account under the app's **User Management** (development mode only allows listed users).
5. Download `spotify-app-remote-release-*.aar` from https://github.com/spotify/android-sdk/releases and commit it to `app/libs/`.

## API keys (no server needed)
Keys are baked into your own APK at build time and never committed. In GitHub: **Settings > Secrets and variables > Actions > New repository secret**, add:
- `XAI_API_KEY` (from https://console.x.ai; the account needs credits)
- `SPOTIFY_CLIENT_ID`
- `SPOTIFY_CLIENT_SECRET`

Optional: set a repository **variable** (not secret) named `XAI_MODEL` to pick a model (default `grok-4-1-fast-non-reasoning`; the app falls back to others automatically). Building locally instead? Put `XAI_API_KEY=...`, `SPOTIFY_CLIENT_ID=...`, `SPOTIFY_CLIENT_SECRET=...` in `local.properties` using the names `xaiApiKey`, `spotifyClientId`, `spotifyClientSecret`.
Keep the repo private and don't share the APK, since the keys live inside it.

## Build APK using GitHub only
1. Create an empty GitHub repository.
2. Upload all project files, preserving folder structure, to the `main` branch.
3. Open **Actions**.
4. Select **Build ATLAS SONORA APK** and choose **Run workflow**. Pushes to `main` also trigger a build.
5. Wait for the run to finish successfully.
6. Open the run and download the `ATLAS-SONORA-debug-apk` artifact.
7. Extract the downloaded ZIP and install `app-debug.apk` on your Android phone.

The generated APK is a debug build for personal testing.

## What the AI does
The curator receives mood, your free-text request, and up to 60 liked/disliked tracks. It returns eight song suggestions with reasons. The app stores feedback locally. AI output can be mistaken or invent a track; verify the exact recording in Spotify search.

## App identity
- Name: ATLAS SONORA
- Package: `com.atlas.sonora`
- Minimum Android: 8.0 (API 26)
- APK build: GitHub Actions
