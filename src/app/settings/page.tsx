import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { AppNav } from "@/components/app-nav";
import { SettingsView } from "@/components/settings/settings-view";
import { pageUser } from "@/lib/server/auth";

export const metadata: Metadata = {
  title: "Settings",
};

export default async function SettingsPage() {
  const user = await pageUser();
  if (!user) redirect("/login?next=/settings");
  return (
    <>
      <AppNav user={user} />
      <main className="flex-1">
        <SettingsView user={user} />
      </main>
    </>
  );
}
