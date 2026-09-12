import { useId } from "react";
import { cn } from "@/lib/utils";

const HEAD =
  "M8.2 18.5C7.8 13.5 8.6 8.2 10.2 4.8C10.5 4.2 11.2 4.1 11.7 4.5C13.6 6.2 15.6 8.6 16.8 10.4C18.9 9.8 21.1 9.8 23.2 10.4C24.4 8.6 26.4 6.2 28.3 4.5C28.8 4.1 29.5 4.2 29.8 4.8C31.4 8.2 32.2 13.5 31.8 18.5C33.6 21.5 33.4 26 31.6 29.2C29.2 33.4 24.8 35.6 20 35.6C15.2 35.6 10.8 33.4 8.4 29.2C6.6 26 6.4 21.5 8.2 18.5Z";
const LEFT_EAR = "M11.4 7.2C12.8 8.6 14.1 10 15 11.3C13.6 11.9 12.4 12.8 11.5 13.8C11.1 11.6 11 9.3 11.4 7.2Z";
const RIGHT_EAR = "M28.6 7.2C27.2 8.6 25.9 10 25 11.3C26.4 11.9 27.6 12.8 28.5 13.8C28.9 11.6 29 9.3 28.6 7.2Z";
const PINK = "#F5A3B7";

export function LogoMark({ className, animated = false }: { className?: string; animated?: boolean }) {
  const id = useId().replace(/:/g, "");
  const blink = animated
    ? { transformBox: "fill-box" as const, transformOrigin: "center", animation: "blink 4.8s ease-in-out infinite" }
    : undefined;
  const wave = (delay: number) =>
    animated ? { animation: `ping-wave 1.6s ease-out ${delay}s infinite`, opacity: 0 } : { opacity: 0 };

  return (
    <svg viewBox="0 0 40 40" className={cn("size-9 shrink-0", className)} aria-hidden>
      <defs>
        <linearGradient id={`head${id}`} x1="10" y1="5" x2="28" y2="36" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#343434" />
          <stop offset="0.55" stopColor="#141414" />
          <stop offset="1" stopColor="#050505" />
        </linearGradient>
        <linearGradient id={`cup${id}`} x1="0" y1="18" x2="0" y2="28" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#5A5A5A" />
          <stop offset="1" stopColor="#1C1C1C" />
        </linearGradient>
      </defs>

      <path d="M2.9 20.8Q1.4 23.1 2.9 25.4" fill="none" stroke="#9A9A9A" strokeWidth="0.9" strokeLinecap="round" style={wave(0)} />
      <path d="M37.1 20.8Q38.6 23.1 37.1 25.4" fill="none" stroke="#9A9A9A" strokeWidth="0.9" strokeLinecap="round" style={wave(0.8)} />

      <path d={HEAD} fill={`url(#head${id})`} stroke="#FFFFFF" strokeOpacity="0.16" strokeWidth="0.8" />
      <path d={LEFT_EAR} fill={PINK} opacity="0.55" />
      <path d={RIGHT_EAR} fill={PINK} opacity="0.55" />

      <path d="M7.7 19.6C7.2 8.6 32.8 8.6 32.3 19.6" fill="none" stroke="#8F8F8F" strokeWidth="2.1" strokeLinecap="round" />
      <path d="M9.4 15.2C12.6 10.4 27.4 10.4 30.6 15.2" fill="none" stroke="#FFFFFF" strokeOpacity="0.22" strokeWidth="0.6" strokeLinecap="round" />
      <rect x="4.6" y="18.2" width="5.6" height="9.4" rx="2.6" fill={`url(#cup${id})`} stroke="#0A0A0A" strokeWidth="0.6" />
      <rect x="29.8" y="18.2" width="5.6" height="9.4" rx="2.6" fill={`url(#cup${id})`} stroke="#0A0A0A" strokeWidth="0.6" />
      <rect x="5.6" y="19.7" width="1.3" height="6.4" rx="0.65" fill="#FFFFFF" opacity="0.2" />
      <rect x="33.1" y="19.7" width="1.3" height="6.4" rx="0.65" fill="#FFFFFF" opacity="0.2" />

      <g style={blink}>
        <ellipse cx="15.2" cy="22.6" rx="3.1" ry="3.5" fill="#FFFFFF" />
        <ellipse cx="24.8" cy="22.6" rx="3.1" ry="3.5" fill="#FFFFFF" />
        <ellipse cx="15.7" cy="23.2" rx="2" ry="2.5" fill="#0A0A0A" />
        <ellipse cx="24.3" cy="23.2" rx="2" ry="2.5" fill="#0A0A0A" />
        <circle cx="14.7" cy="21.8" r="0.9" fill="#FFFFFF" />
        <circle cx="23.3" cy="21.8" r="0.9" fill="#FFFFFF" />
        <circle cx="16.5" cy="24.4" r="0.4" fill="#FFFFFF" opacity="0.85" />
        <circle cx="25.1" cy="24.4" r="0.4" fill="#FFFFFF" opacity="0.85" />
      </g>

      <ellipse cx="11.9" cy="27.5" rx="1.7" ry="0.95" fill={PINK} opacity="0.35" />
      <ellipse cx="28.1" cy="27.5" rx="1.7" ry="0.95" fill={PINK} opacity="0.35" />
      <path d="M18.8 27.4C19.4 27 20.6 27 21.2 27.4C21 28.2 20.5 28.7 20 28.8C19.5 28.7 19 28.2 18.8 27.4Z" fill={PINK} />
      <path
        d="M18.1 29.6C18.8 30.4 19.6 30.3 20 29.5C20.4 30.3 21.2 30.4 21.9 29.6"
        fill="none"
        stroke="#BDBDBD"
        strokeWidth="0.7"
        strokeLinecap="round"
      />
    </svg>
  );
}

export function Logo({ className, animated }: { className?: string; animated?: boolean }) {
  return (
    <span className={cn("inline-flex items-center gap-2", className)}>
      <LogoMark animated={animated} className="size-10" />
      <span className="font-display text-[1.08rem] leading-none tracking-[-0.02em]">
        <span className="font-bold">nigix</span>
        <span className="font-light opacity-60">mosha</span>
      </span>
    </span>
  );
}
