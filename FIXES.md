# Fixes And Improvements

Every bug fixed and every change made to the Android client, written so the same work can be
repeated on iOS. Ordered by area rather than by date, because that is how you would port it.

Sections 1-11 are the pre-launch audit and everything after it, in detail. Section 12 covers
the feature work that came before, in less depth - it was construction rather than repair, so
there is no root cause to record.

Each entry gives the **symptom** a user saw, the **cause**, the Android **fix**, and what iOS
needs. The iOS column is not a guess: the statuses below were read out of
`ios/Instafact.swiftpm/Sources/InstafactApp/` in the main repo on 2026-09-03. Anything marked
*unverified* was not checked and should be before it is trusted.

> This repository is public. Credentials, server addresses and the review account are
> deliberately absent from this document.

## Port Checklist

| # | Issue | iOS status |
| --- | --- | --- |
| 1 | Token refresh sent the wrong wire names | **Already correct** (`APIClient.swift`) |
| 2 | Phone numbers typed with a country code rejected on device | **Same bug** (`Countries.swift`) |
| 3 | Notifications leaked to the next account on a shared device | **Same bug** (`SessionStore.swift`) |
| 4 | Session tokens copied into cloud backup | **Same exposure** (`UserDefaults`, no exclusion) |
| 5 | Home did not show a new submission or update its status | **Partial** — reloads after submit, no polling, no refresh on foreground |
| 6 | Chat did not scroll to the newest message | **Already correct** (`ChatView.swift`) |
| 7 | Chat input hidden behind the keyboard | **Not applicable** — SwiftUI insets handle it |
| 8 | Send gave no feedback for 10-30s | **Already correct** — optimistic bubble + `TypingBubble` |
| 9 | Hardcoded chat suggestion chips | **Same issue** (`ChatView.swift:62`) |
| 10 | No way to report an issue | **Missing** — no `/report-issue` caller |
| 11 | In-app browser accepted any URL scheme | **Same gap** (`AppStore.openInApp`) |
| 12 | Push with `query_id` 0 deep linked to a dead screen | **Already correct** (`AppStore.swift`) |
| 13 | Instagram `/p/` posts rejected by the client | **Already correct** (`VideoURL.swift`) |
| 14 | Onboarding shown to signed-in users | **Already correct** (gated on `hasSignedIn`) |
| 15 | Failed checks could not be retried | **Already correct** (`retryCheck`) |
| 16 | Release build could not reach the server at all | **Android-only** (R8) |
| 17 | `java.time` crashed every timestamp screen on Android 7 | **Android-only** |
| 18 | Tokens and phone numbers written to the log | *unverified* |
| 19 | Cleartext HTTP allowed app-wide | **Likely fine** — no ATS exception in `Info.plist` |

---

## 1. Authentication And Session

### 1.1 Token refresh had never worked

**Symptom.** Every user was silently signed out about a week after logging in.

**Cause.** `TokenRefreshAuthenticator` constructed its own bare `Gson()` instead of the
configured instance. The app-wide instance sets
`FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES`; a default one does not. So the refresh call
serialised `{"refreshToken": "..."}` where the API requires `{"refresh_token": "..."}`, the
server rejected it, and the only path that could renew an expired session always failed.

This is invisible in testing because nothing goes wrong until an access token actually
expires — days after any manual test finishes.

**Fix.** Hoisted the configured instance to `NetworkModule.gson` and injected it into the
authenticator. Added `WireFormatTest` asserting the serialised names of the auth payloads, so
the field names are pinned by a test rather than by a naming policy that one line of code can
opt out of.

**iOS.** Already correct — `APIClient.swift` builds the body explicitly as
`["refresh_token": refreshToken]`, which cannot drift the same way. Worth keeping explicit
rather than moving to a `Codable` policy.

### 1.2 Phone numbers typed with their country code were rejected

**Symptom.** "Enter a valid 10-digit number for India" for a number that is perfectly valid,
typed as `+919876500011`. The request never left the device.

**Cause.** The server already normalises `+91…`, `91…` and `0…` to the national number. The
client stripped non-digits only, so a typed country code left 12 digits and failed a
`digits.length != 10` check.

Anyone pasting from their contacts hits this, and so does a Play Store reviewer handed the
review account written in full — for them it is a total sign-in failure and a rejected review.

**Fix.** New `PhoneNumberInput` (moved out of `LoginViewModel`, since none of it depended on
ViewModel state and the live validation and the send path were free to drift apart). A dial
code is stripped **only when what remains is exactly the national length**, so a real number
that happens to start with those digits is left alone. One leading trunk zero is dropped on
the same condition. Eight unit tests cover it.

**iOS. Same bug.** `Countries.validate` (`Countries.swift:130`) does
`digits.count != expected` against digits filtered from the raw input, with no dial-code
handling. Port `PhoneNumberInput.normalize` before `validate` runs.

### 1.3 Registration accepted numbers that can never receive an SMS

Mirrored the server's rules client-side: Indian mobiles start 6-9, and all-same-digit or
perfectly sequential numbers are rejected. Every OTP costs money whether or not it can be
delivered. **iOS already has this** in `Countries.validate`.

### 1.4 Back exited the app during sign-up

`LoginActivity` had no back handling, so back from the OTP step killed the app. Back now
returns to the phone field; the phone step needs two presses within 2s, matching Home.

Also dropped the resend countdown from 90s to 60s to match the server — the button was
enabling before a resend was actually allowed.

---

## 2. Privacy On A Shared Device

### 2.1 Notifications leaked across accounts

**Symptom.** Sign out, sign in as someone else, open notifications — the previous account's
fact-check results are listed.

**Cause.** `NotificationStore` persists received pushes locally and nothing cleared it on
logout.

**Fix.** `PreferenceManager.clearUserSession()` now clears the notification store first, and
the store is excluded from backup.

**iOS. Same bug.** `SessionStore.clearUserSession()` (`SessionStore.swift:46`) removes the
session keys and deliberately keeps the push token, but never touches `NotificationStore`.
`AppStore.logout()` clears in-memory feeds only.

### 2.2 Session tokens were copied to cloud backup

**Symptom.** A fresh install came back already signed in, with the previous history — making
a genuine first-run test impossible.

**Cause.** `allowBackup` with no rules meant `instafact_prefs.xml` went to Google Drive.
Beyond the testing nuisance: auth and refresh tokens sat in cloud storage, restoring the
backup on another handset handed over a live signed-in session, and the device-specific FCM
token was restored onto the wrong device.

**Fix.** Excluded from both `backup_rules.xml` (Android 11 and below) and
`data_extraction_rules.xml` (API 31+ cloud backup and device transfer). Both are needed —
they cover different OS versions and `minSdk` is 24.

**iOS. Same exposure, different mechanism.** The session lives in `UserDefaults`, which is
included in iCloud and encrypted local backups. Tokens belong in the Keychain with
`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`, which is excluded from backup by
definition. At minimum move `authToken` and `refreshToken`.

### 2.3 Tokens and phone numbers written to logcat

`SessionDebugLogger` printed auth tokens, refresh tokens and phone numbers. Any app holding
`READ_LOGS`, and anyone with the device on USB, could read them. Every function now returns
immediately unless `BuildConfig.DEBUG`, and release builds additionally strip `Log.d`/`Log.v`
bodies via `-assumenosideeffects`.

**iOS.** *Unverified* — audit any `print`/`os_log` on the auth path and gate it on `#if DEBUG`.
Note that `os_log` interpolations are private by default but `print` is not.

---

## 3. Home And The Submission Lifecycle

### 3.1 A newly submitted reel did not appear, and its status never changed

**Symptom.** Share a reel, land on Home, nothing there. Pull to refresh and it appears —
stuck on "pending" forever until another manual refresh.

**Cause.** Three separate gaps, not one:

1. `submitSharedUrl` set the submit state and left the reload to whichever screen happened to
   be observing — the share-sheet path had no such screen.
2. Nothing refreshed when Home came back into view.
3. Nothing polled while a check was still running, so a row's status was only ever as fresh
   as the last manual pull.

**Fix.** `HomeViewModel` now:

- pulls the new row in itself on submit success, so both the share sheet and the in-app paste
  get it;
- refreshes quietly on resume;
- polls every 5s while any row is `pending` or `processing`, stopping the moment none are,
  and giving up after five minutes so a wedged query cannot poll forever;
- cancels polling in `onCleared()`.

Background refreshes deliberately do **not** flip the screen to a spinner or replace the list
with an error — that would blank content the user is already reading. Hence
`loadHistory(showLoading: Boolean)` and `refreshHistoryQuietly()`.

**iOS. Partial.** `AppStore.submit` already calls `loadHistory()` on success, so symptom one
does not occur. There is no polling and no refresh when the app returns to the foreground, so
a row still sits on "pending" until the user pulls. Port the poll loop (a cancellable `Task`
keyed on whether any row is in flight) and refresh on `scenePhase == .active`.

### 3.2 Failed checks could not be retried

Added a retry button on a failed result. No backend change was needed — `submit_query`
already retires a failed query and schedules a replacement — but **the client must follow the
new query id the server returns** rather than keep polling the old one. **iOS already handles
this** in `retryCheck`, including that note.

### 3.3 The empty home screen

Replaced a paragraph of prose with a three-step visual guide built from real Instagram
screenshots, in a single card below the paste box. Someone scanning an empty screen follows
numbered steps but does not read prose.

Sized to fit unscrolled, which meant dropping the list's bottom-nav clearance while the guide
is showing — with no history there is no card to clear, and that padding was the only thing
pushing the guide past the fold. `HomeFeedFragment` switches bottom padding between 12dp
(empty) and 104dp (populated).

### 3.4 Loading read as a hang

Shortened the copy, raised it to 17sp, and added shimmer placeholder bars so a wait reads as
work in progress. `ShimmerView` is hand-rolled — the whole effect is one translated gradient —
and it stops animating when detached, so it does not burn cycles off screen. **iOS has an
equivalent** in `Components.swift`.

---

## 4. The Follow-Up Chat

### 4.1 The input sat behind the keyboard

**Cause.** `configureSystemBars` calls `setDecorFitsSystemWindows(false)`, which puts the app
in charge of its own insets. In that mode the system does not resize the window for the IME
and `windowSoftInputMode="adjustResize"` is **ignored** — but `applySystemBarInsets` only ever
consumed `Type.systemBars()`.

**Fix.** `applySystemBarAndImeInsets` pads the bottom by the **larger** of the system-bar and
IME insets — the larger rather than the sum, because an open keyboard already covers the
navigation bar and adding both leaves a nav-bar-sized gap.

**iOS.** Not applicable; SwiftUI moves the safe area for the keyboard by default. The trap is
the reverse one — an `ignoresSafeArea(.keyboard)` added for a background will take the input
with it if it is applied too high up the view tree.

### 4.2 Sending gave no feedback for 10-30 seconds

**Symptom.** Tap send, the screen sits perfectly still for the length of a round trip, then
jumps straight to a finished conversation. Users tapped send repeatedly.

**Fix.** The question and a typing bubble are appended locally the instant send is tapped, the
input clears, and the send button dims to 45% so a second tap plainly does nothing. The
server's list replaces the placeholders when the reply lands. Placeholders use **negative
ids** (`LocalChatIds`) so they cannot collide with server rows and `DiffUtil` swaps them
cleanly, and the typing bubble is shaped like a real assistant message so the answer replaces
it in place.

Clearing the input on tap means a failed send would have thrown away what was typed, so the
question is restored to the box on error. **iOS already does all of this.**

### 4.3 The list did not follow the newest message

**Symptom.** Send a message; it posts, but the view stays put. No way to tell whether it sent,
or whether a reply is being written.

**Cause.** `ChatAdapter` is a `ListAdapter`, which diffs on a background thread. The
`scrollToPosition` call sat on the line *after* `submitList`, so it ran while the adapter
still held the old list and scrolled to where the conversation already was.

**Fix.** The scroll moved into the `submitList` commit callback, posted (the new row is not
laid out at that instant) and re-reading the index from the adapter so a list that changed
again cannot scroll past the end. Also scrolls when the input takes focus, since opening the
keyboard halves the visible list.

**iOS already correct** — `ChatView` scrolls in `onChange(of: model.messages)`, which fires
after the data is in hand.

### 4.4 Hardcoded suggestion chips

Three fixed prompts appeared on every reel regardless of subject, so they read as filler.
Removed the strip, the strings, the handlers and the drawable.

**iOS. Same issue** — `ChatView.swift:62` hardcodes
`["Show me studies", "Is this safe?", "Explain the verdict"]`. Remove `suggestionRow` and the
`suggestions` constant.

### 4.5 The follow-up guardrail refused on-topic questions *(server-side)*

The prompt conflated *topic* with *source*: "answer only about this video's transcript" plus
"refuse anything unrelated to this video" meant the model read **not in the video** as
**unrelated**, and refused reasonable questions about the subject matter.

Rewritten so scope is the video **and the subject matter its claims are about**, stating
outright that being unanswerable from the transcript is not grounds to refuse — answer from
general knowledge and say where the answer came from. Genuinely off-topic requests are still
refused, now with a redirect to what the assistant can do.

Both clients get this for free. Note the tradeoff: general-knowledge answers on this path have
no web search behind them, so they can be wrong in ways the fact-check pipeline is not. The
prompt requires flagging uncertainty and forbids inventing studies, but that is an
instruction, not a guarantee.

---

## 5. Reporting And Feedback

### 5.1 Report an issue

New entry in Profile below Help & support: six preset categories
(`wrong_result`, `crash`, `link_not_working`, `notifications`, `login`, `other`), an optional
free-text description, posting to `POST /report-issue` with the app version, device model and
OS version attached.

Unlike the rating prompt, this **surfaces its failure and keeps the dialog open with the text
intact** — the user pressed a button that said "Send report", and the description is their
work to lose.

**iOS. Missing entirely.** No `/report-issue` caller exists. Request body:

```json
{ "user_id": 0, "category": "wrong_result", "description": "", "app_version": "",
  "device": "iPhone15,2", "os_version": "iOS 18.2" }
```

Unknown categories are stored, not rejected, so the two clients need not agree on the list.

### 5.2 Rating flow

Star dialog on the first completed result and from Profile. 4-5 goes to the store; 1-3 opens
an in-app form with preset reasons and free text, posted in full to `POST /app-feedback`
because the analytics copy is capped at 100 characters.

Only prompts on a **resolved** result — a spinner or an error is the worst possible moment to
ask how much someone likes the app. Profile's "Rate us" always works, since tapping it is an
explicit request. Sent on an application scope because the dialog is dismissed immediately;
failures are logged, not shown. **iOS has this.**

### 5.3 Unsupported platforms

Replaced the toast with a dialog that declines warmly and promises more platforms — a toast
that vanishes reads as the app ignoring you. Each rejection is counted with its platform, to
show what to build next.

---

## 6. Notifications

- **`query_id` 0 deep linked into nothing.** It is the backend's "nothing to open" marker for
  welcome greetings; tapping one opened a detail screen for a query that does not exist and
  showed "Could not open this result". Those now open Home. **iOS already correct.**
- In-app notification centre backed by the data-only FCM payload
  (`NotificationsActivity`, `NotificationStore`).
- Push delivered vs opened is tracked separately, since the gap between them is the only
  measure of whether the copy is working.

---

## 7. Safety

### 7.1 Unguarded `ACTION_VIEW` launches

`openVideoLink` sits on every result screen and takes a **backend-supplied URL**. An intent
with no handler throws `ActivityNotFoundException` and crashes the app. Both call sites are
now wrapped.

### 7.2 In-app browser accepted any URL

Now loads `http(s)` only, with anything else handed to the system; `allowFileAccess` and
`allowContentAccess` are off; the WebView is detached and destroyed in `onDestroy` (it holds
an Activity context and leaks the whole screen otherwise).

**iOS. Same gap.** `AppStore.openInApp` (`AppStore.swift:522`) does
`guard let url = URL(string: urlString)`, which accepts any scheme, and hands it straight to
`WKWebView.load`. Add the same `http`/`https` allowlist. These URLs come from model output and
from the references list, so they are not trusted input.

### 7.3 Cleartext traffic

`usesCleartextTraffic` was on app-wide. Replaced with a network security config that permits
cleartext **only** for localhost, and only in debug builds, via a `src/debug` override.

**iOS.** `Info.plist` has no `NSAppTransportSecurity` dictionary, so ATS defaults apply and
this is already fine. Keep it that way — do not add an exception for a local dev server.

### 7.4 URL validation matched to the backend

The client rejected Instagram posts (`/p/`) that the backend has been fact-checking from
images for some time. Host rules now match the backend and only a leading `www.` is stripped.
**iOS already correct** (`VideoURL.swift:13`).

---

## 8. Release Build *(Android-only, recorded for completeness)*

### 8.1 The release APK could not talk to the server at all

`proguard-rules.pro` was **empty** while `minifyEnabled` was on. R8 renamed and stripped the
API model classes that Gson resolves reflectively, so the release build installed, launched,
and failed every request. Debug builds were unaffected, which is exactly why it survived so
long.

Fixed with a full rule set: `Signature`/`InnerClasses`/`EnclosingMethod`/annotation
attributes, keeps on `data.model.**`, enums, Retrofit, Gson, OkHttp, Firebase, Markwon and
custom views. Verified against the compiled output rather than by inspection — `mapping.txt`
went from 13 to 29 preserved classes, confirmed in `dexdump`.

### 8.2 `java.time` crashed on Android 7

`minSdk` is 24; `java.time` is API 26+. Every timestamp screen crashed on Android 7.x. Fixed
by enabling core library desugaring.

### 8.3 Build-configuration guards

- `verifyReleaseConfig` fails a release build unless `API_BASE_URL` is set, `https`, and
  slash-terminated.
- `verifyFirebaseConfig` and unconditional application of the Google Services plugin, so a
  build cannot silently ship with push and analytics switched off.
- Version code and name moved to `gradle.properties` as the single place to bump.

---

## 9. Screens And Copy

- "Chat with Expert" → "Chat with InstaFact AI". It is an AI; calling it an expert oversells
  it.
- The confidence gauge explains itself in a dialog on the `?`.
- Age group is a dropdown rather than six wrapped chips.
- Removed the star button in Explore (it did nothing) and the unimplemented "See all" links.
- The Profile notification dot reflects the unread count instead of always showing.
- Complete-profile dialog rebuilt to match the app — branded tile, labelled fields, gradient
  CTA, inline errors — instead of a stock alert dialog.
- Country picker for OTP login.
- First-run spotlight coach-mark tour.
- Onboarding walkthrough is for **signed-out users only**; a testing override was forcing it
  on every launch. The guard lives in the activity as well as the splash routing, because the
  notification deep link and the share flow can reach that screen without passing the splash.
  **iOS already correct.**
- Every activity locked to portrait.

---

## 10. Analytics

`Analytics.kt` is the single typed surface for every event, because GA silently creates a new
event for a typo and it can never be renamed or merged afterwards.

Covers the submit → result funnel with platform and entry point, the verdict and confidence
**actually shown**, onboarding drop-off, auth, engagement, push delivered vs opened, and
screen views.

**No PII**: phone numbers, names, tokens, chat text and reel URLs stay out. Reels are keyed by
query id; chat logs message length only.

One trap worth repeating: use a dedicated `analyticsPlatform()` rather than the display-label
helpers. Those return localized strings and would split every report by device language.
**iOS mirrors these event names** in `Analytics.swift` — keep them identical or the two
platforms cannot be compared in one report.

---

## 11. Server-Side Changes The Clients Depend On

- `POST /report-issue` — see 5.1.
- `POST /app-feedback` — full-length rating comments.
- Follow-up chat guardrail rewritten — see 4.5.
- A review account that bypasses SMS for store review, gated by a constant-time comparison
  and failing closed. Credentials live only in the server environment.
- Per-query token and cost tracking, priced from published model rates rather than invoices,
  surfaced with logs, failures, reports and external-service health in an admin dashboard.

---

## 12. Earlier Build-Out (May - August 2026)

The sections above cover the pre-launch audit and everything after it, where each change
answered a specific defect. What follows is the feature work that came before, listed so this
document genuinely covers the app from its first commit. These are constructions rather than
fixes, so there is no "cause" to record - but iOS needs the same behaviour, and a few carry
decisions that are easy to get wrong twice.

### 12.1 Fact-check results are markdown, not plain text

The backend returns markdown in the summary, explanation and chat replies. Rendering it raw
put literal `**` and `##` on screen.

`MarkdownRenderer` wraps Markwon with `CorePlugin` and `LinkifyPlugin`, sets
`LinkMovementMethod`, and routes every link tap through a callback instead of letting the
`TextView` fire its own intent - that is what keeps sources in the in-app browser rather than
ejecting the user to Chrome. `highlightColor` is cleared because Markwon's default selection
flash looks like a rendering bug on a coloured card.

**iOS.** `RichText.swift` covers this. The link-callback point matters equally there: a
`Text` with a markdown link opens Safari unless the tap is intercepted.

### 12.2 On-device video metadata

`VideoMetadataFetcher` resolves a title, channel name, creator id and thumbnail on the client
before submitting, so a history row has something to show immediately instead of a bare URL
while the check runs. YouTube goes through oEmbed; Instagram is scraped from the page with a
browser user agent and a 12s timeout.

The Instagram path is inherently fragile - it depends on page markup that Instagram can change
without notice. It must fail soft: a null return has to leave the submission working with no
metadata, never block or fail it.

**iOS.** `VideoMetadataFetcher.swift` does the YouTube oEmbed half. Check whether the
Instagram path exists and whether both fail soft.

### 12.3 Result feedback

Helpful / not helpful on a result, posted per query with the verdict attached, so accuracy
complaints can be read against what was actually shown. The vote is cached locally so the
buttons stay in their voted state across restarts rather than inviting an endless re-vote.

### 12.4 Screens built in this period

Bottom navigation and a drawer; Explore with trending, most shared and recently verified;
Profile with an edit sheet and a photo flow that uploads through a pre-signed URL rather than
sending image bytes to the API; Help & Support with an FAQ list; the in-app browser; the
five-slide onboarding walkthrough; the chat thread itself; and the detail screen.

The detail screen was reworked twice - once for layout and once when the backend's result
sections changed shape - and is the densest screen in the app: verdict header, confidence
gauge, tabbed summary and references, feedback row, retry, and the entry point into chat.

Also from this period: the Inter font family, the verdict icon set, and the profile header
showing the user's real initial rather than a placeholder.

---

## Commit Index

| Commit | Date | Summary |
| --- | --- | --- |
| `9e0d049` | 2026-09-03 | Chat list follows the newest message; suggestion chips removed |
| `8774d33` | 2026-09-03 | Home auto-refresh and polling; Report an issue |
| `b3eb4d1` | 2026-09-03 | Phone numbers with a country code accepted |
| `b90c950` | 2026-09-03 | Chat keyboard insets; optimistic send and typing bubble |
| `f4b5f96` | 2026-09-02 | Release build made shippable; empty home reworked; safety fixes |
| `c35f535` | 2026-08-31 | Retry, share guide, shimmer, profile dialog, phone rules, back handling |
| `ca82e9a` | 2026-08-31 | Onboarding gated on sign-in; session excluded from backup |
| `a9c80ea` | 2026-08-31 | Analytics, rating flow, unsupported-platform dialog, portrait lock |
| `57569eb` | 2026-08-31 | Country picker, notification centre, walkthrough and detail rework |
| `bdf11c2` | 2026-08-14 | Detail and profile layout rework, Inter font, verdict icons |
| `b238cb1` | 2026-07-24 | Markdown rendering for results and chat replies |
| `c41404a` | 2026-07-21 | Video metadata fetcher, in-app browser, Help & Support, edit profile |
| `5c76ce3` | 2026-05-27 | Real profile initial on the home header |
| `6e67af9` | 2026-05-26 | Bottom nav, drawer, Explore, Profile, walkthrough, chat |
| `491075d` | 2026-05-12 | Initial Android app commit |
