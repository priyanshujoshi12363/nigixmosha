import { Footer } from "@/components/footer";
import { Cta } from "@/components/landing/cta";
import { Engines } from "@/components/landing/engines";
import { Features } from "@/components/landing/features";
import { Hero } from "@/components/landing/hero";
import { How } from "@/components/landing/how";
import { LanguagesSection } from "@/components/landing/languages";
import { SiteNav } from "@/components/site-nav";

export default function Home() {
  return (
    <>
      <SiteNav />
      <main className="flex-1">
        <Hero />
        <Engines />
        <How />
        <Features />
        <LanguagesSection />
        <Cta />
      </main>
      <Footer />
    </>
  );
}
