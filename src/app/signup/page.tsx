import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { AuthForm } from "@/components/auth/auth-form";
import { pageUser } from "@/lib/server/auth";
import { safeNextPath } from "@/lib/utils";

export const metadata: Metadata = {
  title: "Create account",
};

export default async function SignupPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const next = safeNextPath((await searchParams).next);
  if (await pageUser()) redirect(next);
  return <AuthForm mode="signup" next={next} />;
}
