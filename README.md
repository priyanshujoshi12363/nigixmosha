# nigixmosha

Turn a novel chapter into a full-cast audiobook. The built-in director reads the chapter, finds every character, casts a voice that fits their personality, and performs each line with emotion — in 40+ languages.

## Run it

```bash
npm install
cp .env.example .env.local
npm run dev
```

Fill in `.env.local`, open http://localhost:3000, create an account and press **Open Studio**.

## Environment

| Variable | Purpose |
| --- | --- |
| `MONGODB_URI` | Accounts, sessions and project history |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Stores finished audiobooks; streams and downloads them as MP3 |
| `OLLAMA_API_KEY` | Key for the director (Ollama Cloud) |
| `OLLAMA_MODEL` | Director model, default `gpt-oss:120b` |
| `OLLAMA_BASE_URL` | Optional; point the director at a self-hosted Ollama instead of Ollama Cloud |
| `SARVAM_API_KEY`, `ELEVENLABS_API_KEY`, `OPENAI_API_KEY`, `GEMINI_API_KEY` | Optional server-side keys for those voice engines |

The director runs only on the server. Its provider and model are never sent to the browser, and users never need an AI key. Voice-engine keys a user types in Settings stay in their browser.

## How it works

1. **Manuscript** — paste one chapter or upload `.txt`, `.md`, `.docx`, `.pdf`, `.html`.
2. **Director** — maps every speaker (gender, age, personality, speaking style), scripts each line with an emotion, and picks a voice for every character from the chosen voice engine. For quoted dialogue the exact text split comes from the source, so lines stay verbatim. If the director is unreachable, a built-in quick reader takes over.
3. **Voices** — the chosen voice engine performs each line in the voice the director picked.
4. **Studio** — lines are trimmed, loudness-matched and stitched with paragraph-aware pauses, then saved to the user's history.

## Accounts & history

- Username + password accounts. Passwords are hashed with scrypt; sessions live in MongoDB behind an httpOnly cookie and expire after 30 days.
- Sign out, sign out of all devices, and change password (which signs out other devices) from **Settings → Account**.
- Studio, History, Settings and every API route require a session.
- Every directed chapter autosaves to **History**. Finished audiobooks upload straight from the browser to Cloudinary with a server-signed ticket, under `nigixmosha/<userId>/<projectId>`, and are deleted with the project.

## Voice engines

Edge Neural Voices (free) · Kokoro (free, self-hosted) · Sarvam Bulbul · ElevenLabs · OpenAI Voice · Gemini Speech · any OpenAI-compatible `/audio/speech` server.

## Stack

Next.js 16 (App Router) · React 19 · TypeScript · Tailwind CSS 4 · Motion · Zustand · MongoDB · Cloudinary.
