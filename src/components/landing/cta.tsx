import { ArrowRight } from "lucide-react";
import { ButtonLink } from "@/components/ui";
import { LogoMark } from "@/components/logo";
import { Reveal } from "./reveal";

export function Cta() {
  return (
    <section className="px-4 py-24 sm:px-6">
      <Reveal>
        <div className="relative mx-auto max-w-6xl overflow-hidden rounded-[2rem] bg-night px-6 py-20 text-center text-white sm:px-12">
          <div aria-hidden className="pointer-events-none absolute inset-0">
            <div className="absolute -left-20 -top-24 h-96 w-96 animate-aurora rounded-full bg-white/10 blur-[110px]" />
            <div
              className="absolute -bottom-32 -right-10 h-[28rem] w-[28rem] animate-aurora rounded-full bg-white/[0.07] blur-[120px]"
              style={{ animationDelay: "-9s" }}
            />
            <div className="absolute left-1/2 top-1/2 h-72 w-72 -translate-x-1/2 -translate-y-1/2 animate-aurora rounded-full bg-white/[0.05] blur-[100px]" />
          </div>
          <div className="relative">
            <LogoMark animated className="mx-auto size-14" />
            <h2 className="mx-auto mt-8 max-w-3xl font-display text-3xl font-semibold leading-[1.1] tracking-[-0.035em] sm:text-5xl">
              Your novel is already a cast of voices. <span className="text-gradient-light">Let them speak.</span>
            </h2>
            <p className="mx-auto mt-5 max-w-lg text-[15.5px] text-white/65">
              Paste a chapter, press direct, and hear your characters for the first time. Free voices are ready, and your studio takes seconds to set up.
            </p>
            <div className="mt-9 flex flex-col items-center justify-center gap-3 sm:flex-row">
              <ButtonLink href="/studio" variant="brand" size="lg" className="group">
                Open the Studio
                <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />
              </ButtonLink>
              <ButtonLink href="/settings" size="lg" className="bg-white/10 text-white shadow-none hover:bg-white/15">
                Connect your engines
              </ButtonLink>
            </div>
          </div>
        </div>
      </Reveal>
    </section>
  );
}
