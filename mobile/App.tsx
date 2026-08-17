import { StatusBar } from "expo-status-bar";
import { useCallback, useEffect, useState } from "react";
import { ActivityIndicator, SafeAreaView, Text, TouchableOpacity, View } from "react-native";
import { ApiRequestError, api, tokenStore } from "./lib/api";
import { colors, styles } from "./lib/theme";
import { DashboardScreen } from "./screens/DashboardScreen";
import { LoginScreen } from "./screens/LoginScreen";
import { RecordScreen } from "./screens/RecordScreen";
import type { AuthUser } from "./lib/types";

type Tab = "dashboard" | "record";

/**
 * 画面遷移はタブ 2 つだけなので、ルーターを入れずに state で切り替えている。
 * Phase 3 で画面が増えたら expo-router の導入を検討する。
 */
export default function App() {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<Tab>("dashboard");

  const restoreSession = useCallback(async () => {
    await tokenStore.load();
    if (!tokenStore.access) {
      setLoading(false);
      return;
    }
    try {
      setUser(await api.me());
    } catch (e) {
      // トークンを捨てるのは「認証が拒否された」ときだけ。
      // サーバーに届かなかっただけでログアウト扱いにすると、
      // 復帰後に再ログインを強いることになる。
      if (e instanceof ApiRequestError && e.status === 401) {
        await tokenStore.clear();
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void restoreSession();
  }, [restoreSession]);

  const signOut = async () => {
    const refreshToken = tokenStore.refresh;
    if (refreshToken) {
      // サーバー側の失効に失敗しても、手元のトークンは必ず捨てる
      await api.logout(refreshToken).catch(() => undefined);
    }
    await tokenStore.clear();
    setUser(null);
  };

  if (loading) {
    return (
      <SafeAreaView style={[styles.screen, { justifyContent: "center", alignItems: "center" }]}>
        <StatusBar style="light" />
        <ActivityIndicator color={colors.accent} />
      </SafeAreaView>
    );
  }

  if (!user) {
    return (
      <SafeAreaView style={styles.screen}>
        <StatusBar style="light" />
        <LoginScreen
          onSignedIn={async () => {
            setUser(await api.me());
          }}
        />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.screen}>
      <StatusBar style="light" />

      <View style={{ flex: 1 }}>
        {tab === "dashboard" ? <DashboardScreen /> : <RecordScreen />}
      </View>

      <View style={styles.tabBar}>
        <TouchableOpacity
          style={[styles.tab, tab === "dashboard" && styles.tabActive]}
          onPress={() => setTab("dashboard")}
        >
          <Text style={[styles.tabText, tab === "dashboard" && styles.tabTextActive]}>
            今日の状況
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tab, tab === "record" && styles.tabActive]}
          onPress={() => setTab("record")}
        >
          <Text style={[styles.tabText, tab === "record" && styles.tabTextActive]}>
            食事を記録
          </Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.tab} onPress={signOut}>
          <Text style={styles.tabText}>ログアウト</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}
