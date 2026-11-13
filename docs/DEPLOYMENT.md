# Build and Deployment

## 1. Build the Android app

Requirements: Android Studio, JDK 17, Android SDK 34.

```sh
./gradlew app:testDebugUnitTest      # run the tests
./gradlew app:assembleDebug          # APK at app/build/outputs/apk/debug/app-debug.apk
```

### Bundle a Gemini key for evaluators (optional, recommended)
Create `local.properties` (it is git-ignored) and add:
```properties
sdk.dir=/path/to/Android/sdk
gemini.api.key=YOUR_GEMINI_KEY
```
Build again. The APK then works with no setup. A user's own key entered in Settings always takes priority. **Never commit `local.properties`.** A key inside an APK can be extracted, so use a free-tier key you can rotate, and restrict it in Google AI Studio.

The API base URL is `API_BASE_URL` in `app/build.gradle.kts`.

## 2. Backend (Node.js 22.12+, PostgreSQL)

```sh
cd backend
cp .env.example .env        # fill in the values below
npm ci
npx prisma migrate deploy
npm start
```

| Variable | Meaning |
|---|---|
| `DATABASE_URL` | PostgreSQL connection string |
| `SESSION_SECRET` | 32+ random bytes for the OAuth session cookie |
| `TOKEN_ENCRYPTION_KEY_B64` | base64 of 32 random bytes, encrypts refresh tokens |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Web OAuth client |
| `GOOGLE_REDIRECT_URI` | `https://<host>/auth/google/callback` |
| `ANDROID_REDIRECT_URI` | `aurviz://oauth/callback` |
| `PUBLIC_BASE_URL` | Public HTTPS URL of the service |
| `ROLE` | `all`, `api` or `worker` |

Generate secrets with `openssl rand -base64 32`.

## 3. Google Cloud setup (for real Gmail)

1. Create a project and enable the **Gmail API**.
2. OAuth consent screen: add the scopes `gmail.send`, `gmail.readonly`, `gmail.compose` (plus `openid`, `email`).
3. Add test users (or publish unverified, see the [Evaluator Guide](EVALUATOR_GUIDE.md#5-testing-with-a-real-gmail-account-optional)).
4. Create an OAuth client of type **Web application** and add the exact HTTPS callback URL.
5. Put the client id and secret in the backend environment (never in the app or the repository).

`gmail.readonly` is a restricted scope: it works for listed test users, and a public release needs Google verification. Accounts connected before the read scopes were added must disconnect and reconnect.

## 4. Hosting notes
- Any host with PostgreSQL works. The reference deployment is a free web service plus a free database, which sleeps when idle. The app wakes it at launch and allows a 45 s first request.
- Run at least one worker (`ROLE=all` does both API and worker).
- Enable database TLS and backups for production.

## 5. Continuous integration
`.github/workflows/build.yml` runs the Android unit tests and builds the APK, and runs the backend tests and `npm audit` on every push.
