# nigixmosha: Google Play submission kit

## Files in this folder

| File | Play Console field |
| --- | --- |
| `nigixmosha-1.0.0.aab` | Release → Production (or Internal testing) → App bundle. Built locally, not committed |
| `icon-512.png` | Store listing → App icon (512×512) |
| `feature-graphic-1024x500.png` | Store listing → Feature graphic |
| `screenshots/01…07.png` | Store listing → Phone screenshots (1080×1920) |

Package name: `com.nigixmosha.app` · versionCode 1 · versionName 1.0.0 · minSdk 26 · targetSdk 36

## Signing

- Upload key: `D:\nigixmosha-signing\upload.jks` (alias `upload`). The passwords are in `D:\nigixmosha-signing\keystore.properties`.
- Back that folder up somewhere safe, like a password manager or a private drive. Without it you cannot ship updates, although you can ask Google for a key reset.
- Enroll in **Play App Signing** when you create the release. Google keeps the app signing key, and your key is only the upload key.
- Build a new bundle with `gradlew bundleRelease`. Increase `versionCode` in `app/build.gradle` for every upload.

## Store listing

**App name:** nigixmosha

**Short description (80 max):**
Turn any story into a full-cast audiobook with AI voices for every character.

**Full description:**
nigixmosha turns your writing into a full-cast audiobook, with a different voice for every character.

Paste a chapter or upload a PDF, DOCX or text file. The AI director reads the story, finds every speaker, and splits it into performed lines with emotion and pacing. Then it casts a voice that fits each character: the narrator, the hero, the grandmother, the stranger at the door.

★ AI director: detects characters, dialogue, narration and the emotion behind every line
★ Automatic casting: dozens of free built-in voices, matched to age, gender and personality
★ Fine control: review the script, change any voice and hear it before you record
★ 27 languages: English, Hindi and many other Indian and world languages
★ Your library, synced: every audiobook is saved to your account, on your phone and on the web
★ Bring your own engine: plug in ElevenLabs, OpenAI, Gemini, Sarvam and more with your own keys, stored encrypted on your device
★ Light and dark themes, built for phones and tablets

For writers, storytellers, teachers and anyone who would rather listen.

made by knoc8

**Category:** Books & Reference (you could also use Productivity)
**Tags:** audiobook, text to speech, storytelling
**Contact email:** your email (required and shown publicly)
**Website:** https://nigixmosha.onrender.com
**Privacy policy URL:** https://nigixmosha.onrender.com/privacy

## App content

- **Ads:** No. The Android app shows no ads.
- **App access:** Login is required. Under "All or some functionality is restricted", add a demo username and password that reviewers can use.
- **Content rating:** Complete the IARC questionnaire. The app has user-generated text that is not shared with other users, no violence, and no gambling, so expect Everyone or 3+.
- **Target audience:** 13 and older (the privacy policy says it is not for children under 13).
- **News app:** No. **Government app:** No. **Financial features:** None. **Health:** None.

### Data safety

- Data collected: **Yes**. Data shared with third parties: **No**. Service providers (Render, MongoDB Atlas, Cloudinary, TTS engines) count as processors, not sharing.
- Encrypted in transit: **Yes** (HTTPS only).
- Users can request deletion: **Yes**. It is in the app under Profile → Delete account, and on the web under Settings → Delete account.
- Account deletion URL: https://nigixmosha.onrender.com/settings

| Data type | Collected | Purpose | Optional |
| --- | --- | --- | --- |
| Personal info → Name (display name) | Yes | Account management, App functionality | No |
| Personal info → User IDs (username) | Yes | Account management | No |
| App activity → Other user-generated content (manuscripts, scripts) | Yes | App functionality | No |
| Audio → Other audio files (generated audiobooks) | Yes | App functionality | No |

The app does **not** collect: location, email, phone, contacts, photos, device IDs, analytics, or crash logs.

### Foreground service declaration (dataSync)

The service type is `FOREGROUND_SERVICE_DATA_SYNC`. Use this text for the justification:

> When the user taps "Produce audiobook", the app generates every line of speech through text-to-speech APIs, merges it into one audio file, and uploads it to the user's library. This transfer takes 1–5 minutes and is started by the user. It keeps running if the user leaves the app, and shows an ongoing notification with progress and a cancel action. It stops as soon as the upload finishes.

Attach a short screen recording that shows the Produce step and the notification. Google asks for a video link.

**Notifications permission** (POST_NOTIFICATIONS) is used only for that production progress notification.

## Release checklist

1. Create the app in Play Console. Choose Free, App, and your default language.
2. Fill in App content (above) and the Store listing (above), and upload the graphics.
3. Testing: new personal developer accounts must run a **closed test with at least 12 testers for 14 days** before Production is unlocked.
4. Upload `nigixmosha-1.0.0.aab`, add release notes, and roll out.

Release notes (1.0.0):
First release: direct, cast and record full-cast audiobooks, with a library synced to the web.
