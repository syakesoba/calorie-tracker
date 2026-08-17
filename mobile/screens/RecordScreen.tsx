import { useCallback, useEffect, useState } from "react";
import {
  ScrollView,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { api, isoDate } from "../lib/api";
import { colors, styles } from "../lib/theme";
import {
  FOOD_SOURCE_LABELS,
  MEAL_TYPES,
  MEAL_TYPE_LABELS,
  type Food,
  type MealLog,
  type MealType,
} from "../lib/types";

/** 記録に積む前の 1 品。栄養値は表示用の見積りで、確定値はサーバーが返す。 */
interface DraftItem {
  food: Food;
  amountG: string;
}

export function RecordScreen() {
  const today = isoDate();
  const [mealType, setMealType] = useState<MealType>("BREAKFAST");
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Food[]>([]);
  const [searching, setSearching] = useState(false);
  const [draft, setDraft] = useState<DraftItem[]>([]);
  const [logs, setLogs] = useState<MealLog[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const loadLogs = useCallback(async () => {
    try {
      setLogs(await api.mealLogs(today));
    } catch (e) {
      setError(e instanceof Error ? e.message : "読み込みに失敗しました。");
    }
  }, [today]);

  useEffect(() => {
    void loadLogs();
  }, [loadLogs]);

  // 入力ごとに検索する。打つたびに投げないよう 300ms 待ってから 1 回だけ呼ぶ。
  useEffect(() => {
    const trimmed = query.trim();
    if (trimmed.length === 0) {
      setResults([]);
      return;
    }
    let cancelled = false;
    setSearching(true);
    const timer = setTimeout(async () => {
      try {
        const found = await api.searchFoods(trimmed);
        if (!cancelled) setResults(found);
      } catch {
        if (!cancelled) setResults([]);
      } finally {
        if (!cancelled) setSearching(false);
      }
    }, 300);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [query]);

  const addToDraft = (food: Food) => {
    setDraft((current) => [...current, { food, amountG: "100" }]);
    setQuery("");
    setResults([]);
  };

  const submit = async () => {
    const items = draft
      .map((item) => ({ foodId: item.food.id, amountG: Number(item.amountG) }))
      .filter((item) => Number.isFinite(item.amountG) && item.amountG > 0);

    if (items.length === 0) {
      setError("分量を正しく入力してください。");
      return;
    }

    setSaving(true);
    setError(null);
    try {
      await api.createMealLog({ eatenOn: today, mealType, items });
      setDraft([]);
      await loadLogs();
    } catch (e) {
      setError(e instanceof Error ? e.message : "記録に失敗しました。");
    } finally {
      setSaving(false);
    }
  };

  const removeLog = async (id: number) => {
    try {
      await api.deleteMealLog(id);
      await loadLogs();
    } catch (e) {
      setError(e instanceof Error ? e.message : "削除に失敗しました。");
    }
  };

  // 表示用の見積り。確定値はサーバーが計算するため、ここでの値は目安。
  const draftKcal = draft.reduce((sum, item) => {
    const amount = Number(item.amountG);
    if (!Number.isFinite(amount)) return sum;
    return sum + (item.food.nutritionPer100g.kcal * amount) / 100;
  }, 0);

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <View style={styles.header}>
        <Text style={styles.title}>食事を記録</Text>
        <Text style={styles.subtitle}>{today}</Text>
      </View>

      {error && <Text style={styles.error}>{error}</Text>}

      <View style={styles.card}>
        <Text style={styles.cardTitle}>食事区分</Text>
        <View style={{ flexDirection: "row", gap: 8 }}>
          {MEAL_TYPES.map((type) => (
            <TouchableOpacity
              key={type}
              style={[
                styles.button,
                styles.buttonSmall,
                styles.flex1,
                mealType !== type && styles.buttonSecondary,
              ]}
              onPress={() => setMealType(type)}
            >
              <Text
                style={[
                  styles.buttonText,
                  { fontSize: 13 },
                  mealType !== type && styles.buttonSecondaryText,
                ]}
              >
                {MEAL_TYPE_LABELS[type]}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        <Text style={styles.label}>食品を検索</Text>
        <TextInput
          style={styles.input}
          value={query}
          onChangeText={setQuery}
          placeholder="ごはん、鶏むね、納豆 …"
          placeholderTextColor={colors.muted}
          autoCapitalize="none"
        />

        {searching && <Text style={styles.muted}>検索中…</Text>}

        {results.map((food) => (
          <View style={styles.listItem} key={food.id}>
            <View style={styles.flex1}>
              <Text style={styles.listName}>{food.name}</Text>
              <Text style={styles.listMeta}>
                {FOOD_SOURCE_LABELS[food.source]} · {food.nutritionPer100g.kcal} kcal / 100g
              </Text>
            </View>
            <TouchableOpacity
              style={[styles.button, styles.buttonSmall, styles.buttonSecondary]}
              onPress={() => addToDraft(food)}
            >
              <Text style={[styles.buttonText, styles.buttonSecondaryText, { fontSize: 13 }]}>
                追加
              </Text>
            </TouchableOpacity>
          </View>
        ))}

        {query.trim().length > 0 && !searching && results.length === 0 && (
          <Text style={styles.muted}>
            該当する食品がありません。Phase 3 で公式の成分表を取り込むまでは、
            代表的な食品のみが登録されています。
          </Text>
        )}
      </View>

      {draft.length > 0 && (
        <View style={styles.card}>
          <Text style={styles.cardTitle}>この食事の内容</Text>
          {draft.map((item, index) => (
            <View style={styles.listItem} key={`${item.food.id}-${index}`}>
              <Text style={styles.listName}>{item.food.name}</Text>
              <TextInput
                style={[styles.input, { width: 78, paddingVertical: 6 }]}
                value={item.amountG}
                onChangeText={(text) =>
                  setDraft((current) =>
                    current.map((d, i) => (i === index ? { ...d, amountG: text } : d)),
                  )
                }
                keyboardType="number-pad"
              />
              <TouchableOpacity
                style={[styles.button, styles.buttonSmall, styles.buttonDanger]}
                onPress={() => setDraft((current) => current.filter((_, i) => i !== index))}
              >
                <Text style={styles.buttonDangerText}>外す</Text>
              </TouchableOpacity>
            </View>
          ))}
          <Text style={styles.muted}>
            合計の目安 約 {Math.round(draftKcal)} kcal — 確定値はサーバー側で計算されます。
          </Text>
          <TouchableOpacity
            style={[styles.button, saving && styles.buttonDisabled]}
            onPress={submit}
            disabled={saving}
          >
            <Text style={styles.buttonText}>
              {saving ? "記録中…" : `${MEAL_TYPE_LABELS[mealType]}として記録する`}
            </Text>
          </TouchableOpacity>
        </View>
      )}

      <View style={styles.card}>
        <Text style={styles.cardTitle}>{today} の記録</Text>
        {logs.length === 0 ? (
          <Text style={styles.muted}>この日の記録はまだありません。</Text>
        ) : (
          logs.map((log) => (
            <View key={log.id} style={{ gap: 2 }}>
              <View style={[styles.listItem, { borderBottomWidth: 2, borderBottomColor: colors.ruleSoft }]}>
                <Text style={styles.listName}>{MEAL_TYPE_LABELS[log.mealType]}</Text>
                <Text style={styles.listMeta}>{Math.round(log.total.kcal)} kcal</Text>
                <TouchableOpacity
                  style={[styles.button, styles.buttonSmall, styles.buttonDanger]}
                  onPress={() => removeLog(log.id)}
                >
                  <Text style={styles.buttonDangerText}>削除</Text>
                </TouchableOpacity>
              </View>
              {log.items.map((item) => (
                <View style={styles.listItem} key={item.id}>
                  <Text style={[styles.listName, { fontWeight: "400" }]}>{item.foodName}</Text>
                  <Text style={styles.listMeta}>{item.amountG} g</Text>
                  <Text style={styles.listMeta}>{Math.round(item.nutrition.kcal)} kcal</Text>
                </View>
              ))}
            </View>
          ))
        )}
      </View>
    </ScrollView>
  );
}
