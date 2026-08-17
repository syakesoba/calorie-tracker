"use client";

import { useCallback, useEffect, useState } from "react";
import { Nav } from "@/components/Nav";
import { api, isoDate } from "@/lib/api";
import { useRequireAuth } from "@/lib/auth";
import {
  FOOD_SOURCE_LABELS,
  MEAL_TYPES,
  MEAL_TYPE_LABELS,
  type Food,
  type MealLog,
  type MealType,
} from "@/lib/types";

/** 記録に積む前の 1 品。栄養値は表示用の見積りで、確定値はサーバーが返す。 */
interface DraftItem {
  food: Food;
  amountG: number;
}

export default function RecordPage() {
  const waiting = useRequireAuth();

  const [date, setDate] = useState(isoDate());
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
      setLogs(await api.mealLogs(date));
    } catch (e) {
      setError(e instanceof Error ? e.message : "読み込みに失敗しました。");
    }
  }, [date]);

  useEffect(() => {
    if (!waiting) void loadLogs();
  }, [waiting, loadLogs]);

  // 入力ごとに検索する。打つたびに投げないよう 250ms 待ってから 1 回だけ呼ぶ。
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
    }, 250);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [query]);

  const addToDraft = (food: Food) => {
    setDraft((current) => [...current, { food, amountG: 100 }]);
    setQuery("");
    setResults([]);
  };

  const updateAmount = (index: number, amountG: number) => {
    setDraft((current) =>
      current.map((item, i) => (i === index ? { ...item, amountG } : item)),
    );
  };

  const removeFromDraft = (index: number) => {
    setDraft((current) => current.filter((_, i) => i !== index));
  };

  const submit = async () => {
    if (draft.length === 0) return;
    setSaving(true);
    setError(null);
    try {
      await api.createMealLog({
        eatenOn: date,
        mealType,
        items: draft.map((item) => ({ foodId: item.food.id, amountG: item.amountG })),
      });
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

  if (waiting) {
    return (
      <main className="shell">
        <p className="muted">読み込み中…</p>
      </main>
    );
  }

  // 表示用の見積り。確定値はサーバーが計算するため、ここでの値は目安。
  const draftKcal = draft.reduce(
    (sum, item) => sum + (item.food.nutritionPer100g.kcal * item.amountG) / 100,
    0,
  );

  return (
    <>
      <Nav />
      <main className="shell">
        <div className="pageHead">
          <h1>食事を記録</h1>
          <span className="sub">{date}</span>
        </div>

        {error && <p className="error">{error}</p>}

        <section className="card">
          <h2>記録する食事</h2>
          <div className="row">
            <label>
              日付
              <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
            </label>
            <label>
              食事区分
              <select
                value={mealType}
                onChange={(e) => setMealType(e.target.value as MealType)}
              >
                {MEAL_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {MEAL_TYPE_LABELS[type]}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <label>
            食品を検索
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="ごはん、鶏むね、納豆 …"
            />
          </label>

          {searching && <p className="muted">検索中…</p>}

          {results.length > 0 && (
            <div className="list">
              {results.map((food) => (
                <div className="listItem" key={food.id}>
                  <span className="name">{food.name}</span>
                  <span className="meta">{FOOD_SOURCE_LABELS[food.source]}</span>
                  <span className="meta num">
                    {food.nutritionPer100g.kcal} kcal / 100g
                  </span>
                  <button type="button" className="secondary" onClick={() => addToDraft(food)}>
                    追加
                  </button>
                </div>
              ))}
            </div>
          )}

          {query.trim().length > 0 && !searching && results.length === 0 && (
            <p className="muted">
              該当する食品がありません。Phase 3 で公式の成分表を取り込むまでは、
              代表的な食品のみが登録されています。
            </p>
          )}
        </section>

        {draft.length > 0 && (
          <section className="card" style={{ marginTop: 16 }}>
            <h2>この食事の内容</h2>
            <div className="list">
              {draft.map((item, index) => (
                <div className="listItem" key={`${item.food.id}-${index}`}>
                  <span className="name">{item.food.name}</span>
                  <label style={{ flex: "0 0 110px" }}>
                    <input
                      type="number"
                      min="1"
                      max="10000"
                      step="1"
                      value={item.amountG}
                      onChange={(e) => updateAmount(index, Number(e.target.value))}
                      aria-label={`${item.food.name} の分量（グラム）`}
                    />
                  </label>
                  <span className="meta num">
                    約 {Math.round((item.food.nutritionPer100g.kcal * item.amountG) / 100)} kcal
                  </span>
                  <button type="button" className="danger" onClick={() => removeFromDraft(index)}>
                    外す
                  </button>
                </div>
              ))}
            </div>
            <p className="muted num">
              合計の目安 約 {Math.round(draftKcal)} kcal
              — 確定値はサーバー側で計算されます。
            </p>
            <button type="button" onClick={submit} disabled={saving}>
              {saving ? "記録中…" : `${MEAL_TYPE_LABELS[mealType]}として記録する`}
            </button>
          </section>
        )}

        <section className="card" style={{ marginTop: 16 }}>
          <h2>{date} の記録</h2>
          {logs.length === 0 ? (
            <p className="muted">この日の記録はまだありません。</p>
          ) : (
            logs.map((log) => (
              <div key={log.id} style={{ marginBottom: 14 }}>
                <div className="listItem" style={{ borderBottomWidth: 2 }}>
                  <span className="name">{MEAL_TYPE_LABELS[log.mealType]}</span>
                  <span className="meta num">{Math.round(log.total.kcal)} kcal</span>
                  <button type="button" className="danger" onClick={() => removeLog(log.id)}>
                    削除
                  </button>
                </div>
                <div className="list">
                  {log.items.map((item) => (
                    <div className="listItem" key={item.id}>
                      <span className="name" style={{ fontWeight: 400 }}>
                        {item.foodName}
                      </span>
                      <span className="meta num">{item.amountG} g</span>
                      <span className="meta num">{Math.round(item.nutrition.kcal)} kcal</span>
                    </div>
                  ))}
                </div>
              </div>
            ))
          )}
        </section>
      </main>
    </>
  );
}
