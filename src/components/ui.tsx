import Link from "next/link";
import type {
  ButtonHTMLAttributes,
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from "react";
import { cn } from "@/lib/utils";

type Variant = "primary" | "brand" | "outline" | "ghost" | "soft" | "danger";
type Size = "xs" | "sm" | "md" | "lg";

const base =
  "relative inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-full font-medium transition-all duration-200 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-tide/60 focus-visible:ring-offset-2 focus-visible:ring-offset-paper disabled:pointer-events-none disabled:opacity-45 active:scale-[0.98]";

const variants: Record<Variant, string> = {
  primary: "bg-ink text-paper hover:bg-ink-2 shadow-[0_10px_30px_-12px_rgba(0,0,0,0.45)]",
  brand:
    "bg-brand text-white shadow-[0_12px_32px_-12px_rgba(0,0,0,0.5)] hover:brightness-125 hover:shadow-[0_18px_44px_-12px_rgba(0,0,0,0.6)]",
  outline: "border border-line bg-white/60 text-ink backdrop-blur hover:border-ink/20 hover:bg-white",
  ghost: "text-ink-2 hover:bg-ink/5",
  soft: "bg-paper-2 text-ink hover:bg-paper-3",
  danger: "bg-ember/10 text-ember hover:bg-ember/15",
};

const sizes: Record<Size, string> = {
  xs: "h-7 px-2.5 text-xs",
  sm: "h-8 px-3.5 text-[13px]",
  md: "h-10 px-5 text-sm",
  lg: "h-12 px-7 text-[15px]",
};

export function buttonClass(variant: Variant = "primary", size: Size = "md", className?: string) {
  return cn(base, variants[variant], sizes[size], className);
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
}

export function Button({ variant, size, className, type = "button", ...props }: ButtonProps) {
  return <button type={type} className={buttonClass(variant, size, className)} {...props} />;
}

export function ButtonLink({
  href,
  variant,
  size,
  className,
  children,
  external,
}: {
  href: string;
  variant?: Variant;
  size?: Size;
  className?: string;
  children: ReactNode;
  external?: boolean;
}) {
  if (external) {
    return (
      <a href={href} target="_blank" rel="noreferrer" className={buttonClass(variant, size, className)}>
        {children}
      </a>
    );
  }
  return (
    <Link href={href} className={buttonClass(variant, size, className)}>
      {children}
    </Link>
  );
}

const field =
  "w-full rounded-xl border border-line bg-white/75 text-sm text-ink placeholder:text-muted/60 outline-none transition focus:border-tide/60 focus:bg-white focus:ring-4 focus:ring-tide/10 disabled:opacity-50";

export function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={cn(field, "h-10 px-3.5", className)} {...props} />;
}

export function Textarea({ className, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={cn(field, "px-4 py-3 leading-relaxed", className)} {...props} />;
}

export function Select({ className, children, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <div className="relative">
      <select className={cn(field, "h-10 appearance-none pl-3.5 pr-9", className)} {...props}>
        {children}
      </select>
      <svg
        viewBox="0 0 20 20"
        className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-muted"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
      >
        <path d="m6 8 4 4 4-4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </div>
  );
}

export function Label({ children, className, htmlFor }: { children: ReactNode; className?: string; htmlFor?: string }) {
  return (
    <label htmlFor={htmlFor} className={cn("mb-1.5 block font-mono text-[10.5px] uppercase tracking-[0.14em] text-muted", className)}>
      {children}
    </label>
  );
}

type Tone = "neutral" | "tide" | "iris" | "pine" | "cobalt" | "ink";

const tones: Record<Tone, string> = {
  neutral: "bg-paper-2 text-ink-2 border-line",
  tide: "bg-tide/10 text-tide border-tide/20",
  iris: "bg-iris/10 text-iris border-iris/20",
  pine: "bg-pine/10 text-pine border-pine/25",
  cobalt: "bg-cobalt/10 text-cobalt border-cobalt/20",
  ink: "bg-ink text-paper border-ink",
};

export function Badge({ children, tone = "neutral", className }: { children: ReactNode; tone?: Tone; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-medium leading-4",
        tones[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}

export function Toggle({
  checked,
  onChange,
  label,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label?: ReactNode;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className="inline-flex cursor-pointer items-center gap-2.5 text-sm text-ink-2"
    >
      <span
        className={cn(
          "relative h-5 w-9 rounded-full transition-colors duration-200",
          checked ? "bg-tide" : "bg-paper-3",
        )}
      >
        <span
          className={cn(
            "absolute top-0.5 size-4 rounded-full bg-white shadow transition-all duration-200",
            checked ? "left-[18px]" : "left-0.5",
          )}
        />
      </span>
      {label}
    </button>
  );
}

export function Slider({
  label,
  value,
  min,
  max,
  step = 1,
  unit = "%",
  onChange,
  disabled,
  hint,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  unit?: string;
  onChange: (v: number) => void;
  disabled?: boolean;
  hint?: string;
}) {
  return (
    <div className={cn(disabled && "opacity-45")} title={disabled ? hint : undefined}>
      <div className="mb-1 flex items-center justify-between text-[11px] text-muted">
        <span className="font-mono uppercase tracking-[0.12em]">{label}</span>
        <span className="tabular-nums text-ink-2">
          {value > 0 ? "+" : ""}
          {Math.round(value)}
          {unit}
        </span>
      </div>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        disabled={disabled}
        onChange={(e) => onChange(Number(e.target.value))}
        className="h-1.5 w-full cursor-pointer"
      />
    </div>
  );
}

export function Initials({ name, color, size = 40, className }: { name: string; color: string; size?: number; className?: string }) {
  const initials =
    name
      .replace(/[^\p{L}\p{N}\s]/gu, "")
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((w) => Array.from(w)[0])
      .join("")
      .toUpperCase() || "?";
  return (
    <span
      className={cn("inline-flex shrink-0 items-center justify-center rounded-full font-semibold text-white", className)}
      style={{
        width: size,
        height: size,
        fontSize: size * 0.36,
        background: `radial-gradient(circle at 30% 25%, color-mix(in oklab, ${color} 55%, white), ${color} 60%, color-mix(in oklab, ${color} 70%, black))`,
        boxShadow: `0 8px 20px -10px ${color}`,
      }}
    >
      {initials}
    </span>
  );
}

export function Bars({
  color = "currentColor",
  count = 5,
  playing = true,
  className,
  height = 18,
}: {
  color?: string;
  count?: number;
  playing?: boolean;
  className?: string;
  height?: number;
}) {
  return (
    <span className={cn("inline-flex items-center gap-[3px]", className)} style={{ height }}>
      {Array.from({ length: count }, (_, i) => (
        <span
          key={i}
          className="w-[3px] origin-center rounded-full"
          style={{
            height: `${40 + ((i * 37) % 60)}%`,
            background: color,
            animation: playing
              ? `wave ${(0.8 + ((i * 13) % 7) / 10).toFixed(2)}s ease-in-out ${(i * 0.09).toFixed(2)}s infinite`
              : undefined,
            transform: playing ? undefined : "scaleY(0.35)",
          }}
        />
      ))}
    </span>
  );
}

export function SectionEyebrow({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn("inline-flex items-center gap-2 font-mono text-[11px] uppercase tracking-[0.2em] text-muted", className)}>
      <span className="h-px w-6 bg-gradient-to-r from-tide to-iris" />
      {children}
    </div>
  );
}
