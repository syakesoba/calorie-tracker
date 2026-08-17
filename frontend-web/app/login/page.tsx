"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiRequestError } from "@/lib/api";
import { useAuth } from "@/lib/auth";

export default function LoginPage() {
  const { user, loading, signIn, signUp } = useAuth();
  const router = useRouter();

  const [mode, setMode] = useState<"login" | "signup">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // 既にログイン済みでこの画面に来た場合は送り返す
  useEffect(() => {
    if (!loading && user) router.replace("/dashboard");
  }, [loading, user, router]);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      if (mode === "login") {
        await signIn(email, password);
      } else {
        await signUp(email, password, displayName);
      }
    } catch (e) {
      // フィールド単位のエラーがあれば、そちらを優先して具体的に見せる
      if (e instanceof ApiRequestError) {
        setError(e.fieldErrors.length > 0 ? e.fieldErrors[0].message : e.message);
      } else {
        setError("予期しないエラーが発生しました。");
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="shell" style={{ maxWidth: 420 }}>
      <div className="pageHead">
        <h1>カロリー管理</h1>
        <span className="sub">{mode === "login" ? "ログイン" : "新規登録"}</span>
      </div>

      <form className="card" onSubmit={submit}>
        {error && <p className="error">{error}</p>}

        <label>
          メールアドレス
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
          />
        </label>

        {mode === "signup" && (
          <label>
            表示名
            <input
              type="text"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              maxLength={50}
              required
            />
          </label>
        )}

        <label>
          パスワード
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete={mode === "login" ? "current-password" : "new-password"}
            minLength={8}
            required
          />
          {mode === "signup" && <span className="muted">8 文字以上</span>}
        </label>

        <button type="submit" disabled={submitting}>
          {submitting ? "処理中…" : mode === "login" ? "ログイン" : "登録する"}
        </button>

        <button
          type="button"
          className="secondary"
          onClick={() => {
            setMode(mode === "login" ? "signup" : "login");
            setError(null);
          }}
        >
          {mode === "login" ? "アカウントを作る" : "ログインに戻る"}
        </button>
      </form>
    </main>
  );
}
