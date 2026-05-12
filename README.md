# Instafact Android App

Production-ready Android client for Instafact built with Kotlin, XML layouts, MVVM, Retrofit, and Firebase Cloud Messaging.

## Structure

```text
android/
  app/
    src/main/java/com/instafact/app/
      ui/
        login/
        home/
        detail/
      data/
        api/
        model/
        repository/
      viewmodel/
      utils/
      fcm/
```

## Tech Stack

- Kotlin
- XML layouts
- MVVM + LiveData
- Retrofit + Gson + OkHttp
- Firebase Cloud Messaging
- SharedPreferences session storage

## Backend Configuration

The app currently defaults to:

```properties
API_BASE_URL=https://cccf-49-36-185-20.ngrok-free.app/
```

That points the app at the current ngrok backend endpoint.

To change it:

1. Open [gradle.properties](/Users/shivam0913/Documents/instafact/android/gradle.properties)
2. Update `API_BASE_URL`
3. Sync/rebuild the project

For a physical device, replace `10.0.2.2` with your machine's reachable LAN IP or your production HTTPS endpoint.

## Firebase Setup

1. Create an Android app in Firebase with package name `com.instafact.app`.
2. Download `google-services.json`.
3. Place it at [google-services.json](/Users/shivam0913/Documents/instafact/android/app/google-services.json).
4. Enable Firebase Cloud Messaging in the Firebase console.
5. Rebuild the project.

Note:
- The Gradle file applies the Google Services plugin only when `google-services.json` exists.
- `google-services.json` is intentionally gitignored and should be added locally per developer machine.
- Without that file, the app still builds, but FCM token generation and push notifications will not work.

## Android SDK Setup

Create a local SDK config file before building:

1. Copy [local.properties.example](/Users/shivam0913/Documents/instafact/android/local.properties.example) to `android/local.properties`
2. Set your real Android SDK path:

```properties
sdk.dir=/Users/<your-user>/Library/Android/sdk
```

You can also use `ANDROID_HOME` if you prefer.

## Build And Run

From the [android](/Users/shivam0913/Documents/instafact/android) directory:

```bash
./gradlew :app:assembleDebug
```

Then install from Android Studio or run:

```bash
./gradlew :app:installDebug
```

## App Flow

### Login

- `LoginActivity` starts OTP registration with `POST /register`
- Supports resend with `POST /resend-otp`
- Completes sign-in with `POST /verify-otp`
- Stores `user_id`, `auth_token`, `phone_number`, and the latest local `fcm_token`

### Share Tray

- `HomeActivity` is registered for `ACTION_SEND` with `text/plain`
- Extracts shared URLs from Instagram Reels and YouTube Shorts
- Immediately calls `POST /submit`
- Returns the user to the home list

### Home

- Loads submissions from `GET /history`
- Shows status and verdict in a RecyclerView
- Opens `DetailActivity` on tap

### Detail

- Loads result from `GET /detail/{id}`
- Sends thumbs up/down to `POST /feedback`
- Prevents duplicate votes locally and respects backend `409` conflicts

### Notifications

- FCM token is requested on app start
- `InstafactFirebaseMessagingService` handles:
  - `onNewToken()`
  - `onMessageReceived()`
- Notifications deep-link into `DetailActivity` using `query_id`

## Expected FCM Payload

Use a data payload that contains:

```json
{
  "query_id": "123"
}
```

Optional notification payload fields like `title` and `body` are also supported.

## Important Notes

- Cleartext traffic is enabled for local development against HTTP backends.
- Switch to HTTPS for production.
- Share handling is wired through `HomeActivity` so the app appears in the Android share sheet.
- The app is placed under `android/` to avoid colliding with the existing backend `app/` package in this repo.
