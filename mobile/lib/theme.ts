import { StyleSheet } from "react-native";

/**
 * 配色とスタイル。
 *
 * Web 版（frontend-web/app/globals.css）と同系の色を使い、
 * 同じアプリだと分かる見た目に揃える。React Native には CSS 変数が無いため、
 * 定数オブジェクトで持つ。
 */
export const colors = {
  paper: "#14171A",
  panel: "#1D2226",
  surface: "#1A1F23",
  ink: "#ECEAE4",
  inkSoft: "#BFC4C8",
  muted: "#8C949A",
  ruleSoft: "#363D43",
  accent: "#55C4A6",
  accentBg: "#16332C",
  warn: "#E08A5F",
  warnBg: "#3A241A",
  protein: "#79AEDD",
  fat: "#E0B25F",
  carb: "#9FC672",
};

/**
 * ダーク固定にしている。栄養記録は朝晩に使うことが多く、
 * 暗所での視認性を優先した。Phase 3 以降でシステム設定への追従を検討する。
 */
export const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: colors.paper,
  },
  content: {
    padding: 18,
    paddingBottom: 48,
    gap: 16,
  },

  // --- ヘッダー ---
  header: {
    paddingTop: 8,
    paddingBottom: 12,
    borderBottomWidth: 3,
    borderBottomColor: colors.ink,
    gap: 4,
  },
  title: {
    color: colors.ink,
    fontSize: 22,
    fontWeight: "800",
  },
  subtitle: {
    color: colors.muted,
    fontSize: 12,
  },

  // --- カード ---
  card: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.ruleSoft,
    padding: 16,
    gap: 12,
  },
  cardTitle: {
    color: colors.inkSoft,
    fontSize: 12,
    fontWeight: "700",
    letterSpacing: 0.5,
    paddingBottom: 8,
    borderBottomWidth: 1,
    borderBottomColor: colors.ruleSoft,
  },

  // --- 数値タイル ---
  tiles: {
    flexDirection: "row",
    gap: 1,
    backgroundColor: colors.ruleSoft,
  },
  tile: {
    flex: 1,
    backgroundColor: colors.surface,
    padding: 12,
    gap: 2,
  },
  tileKey: {
    color: colors.muted,
    fontSize: 10,
    letterSpacing: 1,
  },
  tileValue: {
    color: colors.ink,
    fontSize: 22,
    fontWeight: "800",
  },
  tileValueOver: {
    color: colors.warn,
  },
  tileUnit: {
    color: colors.muted,
    fontSize: 11,
  },

  // --- PFC バー ---
  barRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  barLabel: {
    color: colors.inkSoft,
    fontSize: 12,
    width: 72,
  },
  barTrack: {
    flex: 1,
    height: 8,
    backgroundColor: colors.panel,
    borderWidth: 1,
    borderColor: colors.ruleSoft,
    overflow: "hidden",
  },
  barFill: {
    height: "100%",
  },
  barAmount: {
    color: colors.inkSoft,
    fontSize: 11,
    width: 96,
    textAlign: "right",
  },

  // --- 一覧 ---
  listItem: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: colors.ruleSoft,
  },
  listName: {
    flex: 1,
    color: colors.ink,
    fontSize: 14,
    fontWeight: "600",
  },
  listMeta: {
    color: colors.muted,
    fontSize: 12,
  },

  // --- フォーム ---
  label: {
    color: colors.inkSoft,
    fontSize: 12,
    marginBottom: 4,
  },
  input: {
    backgroundColor: colors.paper,
    borderWidth: 1,
    borderColor: colors.ruleSoft,
    color: colors.ink,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
  },
  button: {
    backgroundColor: colors.accent,
    paddingVertical: 13,
    alignItems: "center",
  },
  buttonText: {
    color: colors.paper,
    fontSize: 15,
    fontWeight: "700",
  },
  buttonSecondary: {
    backgroundColor: "transparent",
    borderWidth: 1,
    borderColor: colors.accent,
  },
  buttonSecondaryText: {
    color: colors.accent,
  },
  buttonSmall: {
    paddingVertical: 7,
    paddingHorizontal: 12,
  },
  buttonDanger: {
    backgroundColor: "transparent",
    borderWidth: 1,
    borderColor: colors.warn,
  },
  buttonDangerText: {
    color: colors.warn,
    fontSize: 12,
  },
  buttonDisabled: {
    opacity: 0.5,
  },

  // --- 通知 ---
  error: {
    backgroundColor: colors.warnBg,
    borderLeftWidth: 4,
    borderLeftColor: colors.warn,
    padding: 12,
    color: colors.ink,
    fontSize: 13,
  },
  notice: {
    backgroundColor: colors.accentBg,
    borderLeftWidth: 4,
    borderLeftColor: colors.accent,
    padding: 12,
    color: colors.ink,
    fontSize: 13,
  },
  muted: {
    color: colors.muted,
    fontSize: 12,
  },

  // --- タブ ---
  tabBar: {
    flexDirection: "row",
    borderTopWidth: 1,
    borderTopColor: colors.ruleSoft,
    backgroundColor: colors.surface,
  },
  tab: {
    flex: 1,
    paddingVertical: 14,
    alignItems: "center",
    borderTopWidth: 3,
    borderTopColor: "transparent",
  },
  tabActive: {
    borderTopColor: colors.accent,
  },
  tabText: {
    color: colors.muted,
    fontSize: 13,
    fontWeight: "600",
  },
  tabTextActive: {
    color: colors.accent,
  },

  row: {
    flexDirection: "row",
    gap: 10,
    alignItems: "flex-end",
  },
  flex1: {
    flex: 1,
  },
});
