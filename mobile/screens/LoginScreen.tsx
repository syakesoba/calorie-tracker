import { useState } from "react";
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { ApiRequestError, api, apiBaseUrl, tokenStore } from "../lib/api";
import { colors, styles } from "../lib/theme";

export function LoginScreen({ onSignedIn }: { onSignedIn: () => void }) {
  const [mode, setMode] = useState<"login" | "signup">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    setError(null);
    setSubmitting(true);
    try {
      const tokens =
        mode === "login"
          ? await api.login(email.trim(), password)
          : await api.signUp(email.trim(), password, displayName.trim());
      await tokenStore.save(tokens);
      onSignedIn();
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
    <KeyboardAvoidingView
      style={styles.screen}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
    >
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.header}>
          <Text style={styles.title}>カロリー管理</Text>
          <Text style={styles.subtitle}>
            {mode === "login" ? "ログイン" : "新規登録"}
          </Text>
        </View>

        {error && <Text style={styles.error}>{error}</Text>}

        <View style={styles.card}>
          <View>
            <Text style={styles.label}>メールアドレス</Text>
            <TextInput
              style={styles.input}
              value={email}
              onChangeText={setEmail}
              autoCapitalize="none"
              keyboardType="email-address"
              autoComplete="email"
              placeholder="taro@example.com"
              placeholderTextColor={colors.muted}
            />
          </View>

          {mode === "signup" && (
            <View>
              <Text style={styles.label}>表示名</Text>
              <TextInput
                style={styles.input}
                value={displayName}
                onChangeText={setDisplayName}
                maxLength={50}
                placeholder="山田太郎"
                placeholderTextColor={colors.muted}
              />
            </View>
          )}

          <View>
            <Text style={styles.label}>パスワード</Text>
            <TextInput
              style={styles.input}
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              autoCapitalize="none"
            />
            {mode === "signup" && <Text style={styles.muted}>8 文字以上</Text>}
          </View>

          <TouchableOpacity
            style={[styles.button, submitting && styles.buttonDisabled]}
            onPress={submit}
            disabled={submitting}
          >
            {submitting ? (
              <ActivityIndicator color={colors.paper} />
            ) : (
              <Text style={styles.buttonText}>
                {mode === "login" ? "ログイン" : "登録する"}
              </Text>
            )}
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.button, styles.buttonSecondary]}
            onPress={() => {
              setMode(mode === "login" ? "signup" : "login");
              setError(null);
            }}
          >
            <Text style={[styles.buttonText, styles.buttonSecondaryText]}>
              {mode === "login" ? "アカウントを作る" : "ログインに戻る"}
            </Text>
          </TouchableOpacity>
        </View>

        {/* どちらの実装に繋がっているかを画面で確認できるようにしておく */}
        <Text style={styles.muted}>接続先 {apiBaseUrl}</Text>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
