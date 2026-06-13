<div align="center">
<h1>FinTrace</h1>
<p><strong>Personal-grade, real-time financial price monitor &amp; alert system for Android.</strong></p>
</div>

FinTrace tracks forex pairs and precious metals (gold/silver) in real time, fires
configurable price-crossing alerts (with TTS, alarm and full-screen delivery), and ships
home-screen widgets. It streams live quotes from the Twelve Data WebSocket API and falls
back to a realistic local simulation when no API key is configured.

## Tech stack

- Kotlin · Jetpack Compose (Material 3)
- Room (persistence) · WorkManager (periodic background sync) · a foreground service for live monitoring
- OkHttp WebSocket (Twelve Data) · Moshi · Kotlin Coroutines / StateFlow
- minSdk 24 · targetSdk 36 · AGP 9.1.1 · Gradle 9.5.1 · JDK 17+

## Run locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio) (or the Android SDK + JDK 17+).

1. Clone the repository and open it in Android Studio.
2. *(Optional)* Create a `.env` file in the project root and set `TWELVE_DATA_API_KEY` to your
   [Twelve Data](https://twelvedata.com/) key to enable live streaming. See `.env.example`.
   Without a key the app runs in simulation mode. (You can also set the key in-app via the
   setup wizard or Settings.)
3. Run the app on an emulator or device. The debug build uses Android's auto-generated debug
   keystore — no manual signing setup or build-file edits are required.

From the command line:

```bash
./gradlew :app:assembleDebug      # build the debug APK
./gradlew :app:testDebugUnitTest  # run unit tests
./gradlew :app:lintDebug          # run Android lint
```

## Release signing

Release builds are signed only when CI/CD provides these environment variables (otherwise the
release variant is left unsigned and debug is used):

`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS` (defaults to `upload`), `KEY_PASSWORD`.

## Continuous integration

`.github/workflows/android.yml` builds the debug APK, runs Android lint and the unit tests on
every push/PR, and uploads the APK plus lint/test reports as artifacts. It needs no repository
secrets to build (the `secrets` Gradle plugin falls back to `.env.example`).
