import type { Metadata } from "next";
import Link from "next/link";
import { Footer } from "@/components/footer";
import { SiteNav } from "@/components/site-nav";

export const metadata: Metadata = {
  title: "Privacy policy",
  description: "How nigixmosha handles your account, manuscripts, audio and API keys.",
};

const UPDATED = "September 22, 2026";

const SECTIONS: { title: string; body: string[] }[] = [
  {
    title: "What we collect",
    body: [
      "Account details: the username and display name you choose, and your password stored only as a salted hash. We never see or store your password in plain text.",
      "Your work: the manuscripts you paste or upload, the cast, scripts and settings of each project, and the audiobooks you record. These are saved to your account so you can reopen them on the web and in the Android app.",
      "Session data: a secure, HTTP-only cookie that keeps you signed in. It is used only to authenticate your requests.",
    ],
  },
  {
    title: "What we do not collect",
    body: [
      "We do not collect your email, phone number, contacts, location, photos or device identifiers, and we do not sell or rent any data.",
      "The voice-engine API keys you add are kept on your own device (browser storage on the web, encrypted storage on Android). They are sent with a request only to reach the engine you chose, and are never saved on our servers.",
    ],
  },
  {
    title: "How your data is used",
    body: [
      "Manuscript text is sent to the AI director and to the text-to-speech engine you pick, only to split the story into lines, cast the characters and generate the narration.",
      "Recorded audiobooks are stored with our media host (Cloudinary) so they can be streamed back to you. Project data is stored in our database (MongoDB Atlas). The service runs on Render.",
      "All traffic between your device and nigixmosha is encrypted with HTTPS.",
    ],
  },
  {
    title: "Advertising",
    body: [
      "The website may show ads served by Google AdSense, which can use cookies to measure and personalise ads. You can manage ad personalisation at adssettings.google.com. The Android app does not show ads.",
    ],
  },
  {
    title: "Retention and deletion",
    body: [
      "Your data stays until you delete it. Deleting a project removes its script and its recorded audio.",
      "You can delete your whole account at any time from Settings on the web or Profile in the Android app. This immediately and permanently removes your account, every project and every recorded audiobook.",
    ],
  },
  {
    title: "Children",
    body: ["nigixmosha is not directed at children under 13, and we do not knowingly collect data from them."],
  },
  {
    title: "Changes and contact",
    body: [
      "If this policy changes, the date at the top of this page is updated. For any privacy question or request, use the contact email listed on our Google Play page.",
    ],
  },
];

export default function PrivacyPage() {
  return (
    <>
      <SiteNav />
      <main className="flex-1">
        <article className="mx-auto max-w-3xl px-6 py-16 sm:py-24">
          <div className="font-mono text-[11px] uppercase tracking-[0.18em] text-muted">Last updated {UPDATED}</div>
          <h1 className="mt-3 font-display text-4xl tracking-tight text-ink sm:text-5xl">Privacy policy</h1>
          <p className="mt-5 text-base leading-relaxed text-ink-2">
            This policy covers the nigixmosha website and the nigixmosha Android app. We keep only what is needed to
            turn your stories into audiobooks, and you can remove all of it whenever you want.
          </p>
          <div className="mt-12 space-y-10">
            {SECTIONS.map((s) => (
              <section key={s.title}>
                <h2 className="text-lg font-semibold text-ink">{s.title}</h2>
                <div className="mt-3 space-y-3 text-sm leading-relaxed text-ink-2">
                  {s.body.map((p) => (
                    <p key={p}>{p}</p>
                  ))}
                </div>
              </section>
            ))}
          </div>
          <div className="mt-14 text-sm text-muted">
            <Link href="/settings" className="text-ink underline underline-offset-4 hover:text-ink-2">
              Manage or delete your account
            </Link>
          </div>
        </article>
      </main>
      <Footer />
    </>
  );
}
