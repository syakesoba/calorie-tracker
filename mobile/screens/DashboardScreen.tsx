import { useCallback, useEffect, useState } from "react";
import {
  RefreshControl,
  ScrollView,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { api, isoDate } from "../lib/api";
import { colors, styles } from "../lib/theme";
import { MEAL_TYPE_LABELS, type DailySummary } from "../lib/types";

export function DashboardScreen() {
  const today = isoDate();
  const [summary, setSummary] = useState<DailySummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [weightInput, setWeightInput] = useState("");
  const [savingWeight, setSavingWeight] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      const daily = await api.dailySummary(today);
      setSummary(daily);
      setWeightInput(daily.weightKg != null ? String(daily.weightKg) : "");
    } catch (e) {
      setError(e instanceof Error ? e.message : "読み込みに失敗しました。");
    }
  }, [today]);

  useEffect(() => {
    void load();
  }, [load]);

  const onRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  const saveWeight = async () => {
    const value = Number(weightInput);
    if (!Number.isFinite(value) || value <= 0) return;
    setSavingWeight(true);
    try {
      await api.putBodyRecord(today, value);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "保存に失敗しました。");
    } finally {
      setSavingWeight(false);
    }
  };

  const goal = summary?.goal ?? null;
  const total = summary?.total ?? null;
  const remaining = summary?.remaining ?? null;
  const overKcal = remaining != null && remaining.kcal < 0;

  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={styles.content}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.accent} />
      }
    >
      <View style={styles.header}>
        <Text style={styles.title}>今日の状況</Text>
        <Text style={styles.subtitle}>{today}</Text>
      </View>

      {error && <Text style={styles.error}>{error}</Text>}

      {!goal && !error && (
        <Text style={styles.notice}>
          目標が未設定です。Web 版の「目標設定」で身体情報を入力すると、
          基礎代謝から目標カロリーを算出できます。
        </Text>
      )}

      {/* --- 数値サマリ --- */}
      <View style={styles.tiles}>
        <View style={styles.tile}>
          <Text style={styles.tileKey}>摂取</Text>
          <Text style={styles.tileValue}>{total ? Math.round(total.kcal) : "—"}</Text>
          <Text style={styles.tileUnit}>kcal</Text>
        </View>
        <View style={styles.tile}>
          <Text style={styles.tileKey}>目標</Text>
          <Text style={styles.tileValue}>{goal ? goal.targetKcal : "—"}</Text>
          <Text style={styles.tileUnit}>kcal</Text>
        </View>
        <View style={styles.tile}>
          <Text style={styles.tileKey}>{overKcal ? "超過" : "残り"}</Text>
          <Text style={[styles.tileValue, overKcal && styles.tileValueOver]}>
            {remaining ? Math.abs(Math.round(remaining.kcal)) : "—"}
          </Text>
          <Text style={styles.tileUnit}>kcal</Text>
        </View>
      </View>

      {/* --- PFC --- */}
      <View style={styles.card}>
        <Text style={styles.cardTitle}>PFC バランス</Text>
        {total ? (
          <>
            <NutrientBar
              label="カロリー"
              color={colors.accent}
              value={total.kcal}
              target={goal?.targetKcal ?? null}
              unit="kcal"
              decimals={0}
            />
            <NutrientBar
              label="たんぱく質"
              color={colors.protein}
              value={total.proteinG}
              target={goal?.targetProteinG ?? null}
              unit="g"
              decimals={1}
            />
            <NutrientBar
              label="脂質"
              color={colors.fat}
              value={total.fatG}
              target={goal?.targetFatG ?? null}
              unit="g"
              decimals={1}
            />
            <NutrientBar
              label="炭水化物"
              color={colors.carb}
              value={total.carbG}
              target={goal?.targetCarbG ?? null}
              unit="g"
              decimals={1}
            />
          </>
        ) : (
          <Text style={styles.muted}>読み込み中…</Text>
        )}
      </View>

      {/* --- 食事区分ごとの内訳 --- */}
      <View style={styles.card}>
        <Text style={styles.cardTitle}>食事区分ごとの内訳</Text>
        {summary && summary.byMealType.length > 0 ? (
          summary.byMealType.map((entry) => (
            <View style={styles.listItem} key={entry.mealType}>
              <Text style={styles.listName}>{MEAL_TYPE_LABELS[entry.mealType]}</Text>
              <Text style={styles.listMeta}>{Math.round(entry.total.kcal)} kcal</Text>
            </View>
          ))
        ) : (
          <Text style={styles.muted}>まだ記録がありません。</Text>
        )}
      </View>

      {/* --- 体重 --- */}
      <View style={styles.card}>
        <Text style={styles.cardTitle}>今日の体重</Text>
        <View style={styles.row}>
          <View style={styles.flex1}>
            <Text style={styles.label}>体重（kg）</Text>
            <TextInput
              style={styles.input}
              value={weightInput}
              onChangeText={setWeightInput}
              keyboardType="decimal-pad"
              placeholder="70.5"
              placeholderTextColor={colors.muted}
            />
          </View>
          <TouchableOpacity
            style={[styles.button, styles.buttonSmall, savingWeight && styles.buttonDisabled]}
            onPress={saveWeight}
            disabled={savingWeight}
          >
            <Text style={styles.buttonText}>{savingWeight ? "保存中…" : "記録"}</Text>
          </TouchableOpacity>
        </View>
        <Text style={styles.muted}>同じ日に再度記録すると上書きされます。</Text>
      </View>
    </ScrollView>
  );
}

/**
 * 目標に対する達成度のバー。
 * 目標が未設定の場合は割合を出せないので、摂取量だけを表示する。
 */
function NutrientBar({
  label,
  color,
  value,
  target,
  unit,
  decimals,
}: {
  label: string;
  color: string;
  value: number;
  target: number | null;
  unit: string;
  decimals: number;
}) {
  const ratio = target && target > 0 ? value / target : 0;
  const over = ratio > 1;
  const widthPercent = Math.min(100, ratio * 100);

  return (
    <View style={styles.barRow}>
      <Text style={styles.barLabel}>{label}</Text>
      <View style={styles.barTrack}>
        <View
          style={[
            styles.barFill,
            { width: `${widthPercent}%`, backgroundColor: over ? colors.warn : color },
          ]}
        />
      </View>
      <Text style={styles.barAmount}>
        {value.toFixed(decimals)}
        {target != null ? ` / ${target.toFixed(decimals)}` : ""} {unit}
      </Text>
    </View>
  );
}
