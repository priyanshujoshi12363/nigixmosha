import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { AppNav } from "@/components/app-nav";
import { Studio } from "@/components/studio/studio";
import { pageUser } from "@/lib/server/auth";

export const metadata: Metadata = {
  title: "Studio",
};

export default async function StudioPage() {
  const user = await pageUser();
  if (!user) redirect("/login?next=/studio");
  return (
    <>
      <AppNav user={user} />
      <main className="flex-1">
        <Studio />
      </main>
    </>
  );
}
