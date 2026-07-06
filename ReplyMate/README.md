# ReplyMate

A fully offline Android app that reads the chat currently on your screen and
suggests 3 replies using a local Gemma model (via MediaPipe LLM Inference).
Nothing leaves the phone.

## How it works

- `ChatAccessibilityService` reads the text nodes visible on screen (same
  mechanism screen readers use) and pushes them into `ConversationBuffer`.
- `OverlayService` watches that buffer, debounces rapid UI updates, and asks
  `LlmHelper` (MediaPipe + Gemma) to generate 3 short replies.
- Suggestions show up as a floating bubble **and** a notification.
- Tapping a suggestion fills it into the currently focused text box via the
  accessibility service. You still tap Send yourself — nothing sends
  automatically.

## Don't have Android Studio? Build it with GitHub instead (free)

You don't need any tooling on your own machine — GitHub's servers can
compile the APK for you.

1. Create a free account at https://github.com if you don't have one.
2. Create a new **public or private** repository (any name, e.g. `ReplyMate`).
3. Upload this whole `ReplyMate` folder into it. Easiest way with no git
   experience: on the repo page, click **Add file → Upload files**, then
   drag in everything from the extracted zip (keep the folder structure —
   GitHub's uploader preserves it if you drag whole folders in Chrome/Edge).
4. Go to the **Actions** tab of your repo. You should see a workflow called
   "Build APK" — click **Run workflow** (or just push a change, it also
   runs automatically on every push to `main`).
5. Wait 3–5 minutes. When it finishes, click on the completed run, scroll to
   **Artifacts**, and download `ReplyMate-debug-apk` — it's a zip containing
   `app-debug.apk`.
6. Transfer that `.apk` to your phone (email it to yourself, upload to
   Google Drive, or plug in via USB and copy it).
7. On your phone, tap the `.apk` file to install. Android will warn about
   "unknown sources" — this is normal for any app not installed from the
   Play Store; you'll need to allow it for your file manager or browser app.

That's it — no Android Studio, no SDK, no command line on your end. GitHub
does the actual compiling.

### If you'd rather build locally without Android Studio
You only need the command-line tools, not the full IDE:
- Install a JDK 17 and the [Android command-line
  tools](https://developer.android.com/studio#command-tools)
- `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"`
- From the project folder: `gradle wrapper` once (or install `gradle`
  directly), then `./gradlew assembleDebug`
- The APK lands in `app/build/outputs/apk/debug/app-debug.apk`

## One-time setup

### 1. Get a model file
Download a `.task` Gemma model built for MediaPipe, e.g. from
https://huggingface.co/litert-community (search "gemma" + "task").
`gemma-2b-it-cpu-int4.task` (~1.3 GB) is a good starting point for most
phones with 6 GB+ RAM. There's also a smaller `gemma-2b-it-cpu-int8` and a
larger, better-quality 4B variant if your phone has 8–12 GB RAM.

### 2. Open in Android Studio
- Open the `ReplyMate/` folder as a project (Android Studio Koala or newer).
- Let it sync Gradle (needs internet the first time, for dependencies).
- Connect your phone (USB debugging on) and hit Run.

### 3. In the app
1. Tap **Choose .task model file** and pick the file you downloaded — it
   gets copied into the app's private storage.
2. Tap **Enable accessibility service**, find ReplyMate in the list, turn it
   on. Android will show a warning because accessibility services can read
   screen content — that's expected, since that's exactly what this needs.
3. Tap **Grant draw-over-apps permission**.
4. Tap **Start ReplyMate**, then open WhatsApp/Instagram/whatever chat app.

A small bubble should appear near the bottom of the screen a second or two
after new messages show up, with 3 suggested replies.

## Tuning

- Tone / style: edit the `tone` default string in `LlmHelper.suggestReplies`
  (e.g. `"flirty and teasing"`, `"formal"`, `"funny, one-liner"`).
- Debounce speed: `DEBOUNCE_MS` in `OverlayService`.
- How much context the model sees: `MAX_LINES` in `ConversationBuffer` and
  `takeLast(12)` in `LlmHelper`.

## Known limitations

- Some apps (mainly banking/payment apps, and some chat apps with
  `FLAG_SECURE`) block accessibility/screenshot access entirely — this is an
  Android-wide restriction, not something the app can bypass.
- First response after opening a chat is slower (model load + first
  generation); after that it's just generation time, roughly 2–8s for a 2B
  model on a mid-range phone.
- This reads *both sides* of a conversation, including messages from the
  other person. Keep that in mind if you'd usually consider that a privacy
  boundary — e.g. don't run this while someone else is using your phone,
  and check the terms of service of apps you use it with.
