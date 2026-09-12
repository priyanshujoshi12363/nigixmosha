import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { AppNav } from "@/components/app-nav";
import { HistoryView } from "@/components/history/history-view";
import { pageUser } from "@/lib/server/auth";

export const metadata: Metadata = {
  title: "History",
};

export default async function HistoryPage() {
  const user = await pageUser();
  if (!user) redirect("/login?next=/history");
  return (
    <>
      <AppNav user={user} />
      <main className="flex-1">
        <HistoryView />
      </main>
    </>
  );
}
