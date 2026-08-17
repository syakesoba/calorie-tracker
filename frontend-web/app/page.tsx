"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useAuth } from "@/lib/auth";

/** ログイン状態に応じてダッシュボードかログイン画面へ振り分けるだけの入口。 */
export default function Home() {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (loading) return;
    router.replace(user ? "/dashboard" : "/login");
  }, [user, loading, router]);

  return (
    <main className="shell">
      <p className="muted">読み込み中…</p>
    </main>
  );
}
