"use client";

import { useRouter } from "next/navigation";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api, tokenStore } from "./api";
import type { AuthUser } from "./types";

interface AuthState {
  user: AuthUser | null;
  /** 起動直後、保存済みトークンの有効性を確認している間は true。 */
  loading: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (email: string, password: string, displayName: string) => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);
  const router = useRouter();

  // 保存されたトークンで /auth/me を引き、有効ならログイン状態を復元する。
  // トークンの有無だけで判断すると、失効済みトークンでも画面が開いてしまう。
  useEffect(() => {
    let cancelled = false;

    (async () => {
      if (!tokenStore.access) {
        if (!cancelled) setLoading(false);
        return;
      }
      try {
        const me = await api.me();
        if (!cancelled) setUser(me);
      } catch {
        tokenStore.clear();
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, []);

  const signIn = useCallback(
    async (email: string, password: string) => {
      tokenStore.save(await api.login(email, password));
      setUser(await api.me());
      router.push("/dashboard");
    },
    [router],
  );

  const signUp = useCallback(
    async (email: string, password: string, displayName: string) => {
      tokenStore.save(await api.signUp(email, password, displayName));
      setUser(await api.me());
      // 登録直後はプロフィールも目標も無いので、初期設定へ送る
      router.push("/setup");
    },
    [router],
  );

  const signOut = useCallback(async () => {
    const refreshToken = tokenStore.refresh;
    if (refreshToken) {
      // サーバー側の失効に失敗しても、手元のトークンは必ず捨てる
      await api.logout(refreshToken).catch(() => undefined);
    }
    tokenStore.clear();
    setUser(null);
    router.push("/login");
  }, [router]);

  const value = useMemo<AuthState>(
    () => ({ user, loading, signIn, signUp, signOut }),
    [user, loading, signIn, signUp, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth は AuthProvider の内側で使ってください");
  }
  return context;
}

/**
 * 未ログインならログイン画面へ送る。認証が必要な画面の先頭で呼ぶ。
 *
 * @returns 表示を待つべき間は true
 */
export function useRequireAuth(): boolean {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !user) router.replace("/login");
  }, [loading, user, router]);

  return loading || !user;
}
