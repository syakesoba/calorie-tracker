"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Nav } from "@/components/Nav";
import { KcalChart, WeightChart } from "@/components/TrendCharts";
import { api, daysAgo, isoDate } from "@/lib/api";
import { useRequireAuth } from "@/lib/auth";
import { MEAL_TYPE_LABELS, type DailySummary, type RangeSummary } from "@/lib/types";

/** グラフに出す日数。 */
const TREND_DAYS = 30;

export default function DashboardPage() {
  const waiting = useRequireAuth();

  const today = isoDate();
  const [summary, setSummary] = useState<DailySummary | null>(null);
  const [range, setRange] = useState<RangeSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [weightInput, setWeightInput] = useState("");
  const [savingWeight, setSavingWeight] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [daily, trend] = await Promise.all([
        api.dailySummary(today),
        api.rangeSummary(daysAgo(TREND_DAYS - 1), today),
      ]);
      setSummary(daily);
      setRange(trend);
      setWeightInput(daily.weightKg != null ? String(daily.weightKg) : "");
    } catch (e) {
      setError(e instanceof Error ? e.message : "読み込みに失敗しました。");
    }
  }, [today]);

  useEffect(() => {
    if (!waiting) void load();
  }, [waiting, load]);

  const saveWeight = async (event: React.FormEvent) => {
    event.preventDefault();
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

  if (waiting) {
    return (
      <main className="shell">
        <p className="muted">読み込み中…</p>
      </main>
    );
  }

  const goal = summary?.goal ?? null;
  const total = summary?.total ?? null;
  const remaining = summary?.remaining ?? null;
  const overKcal = remaining != null && remaining.kcal < 0;

  return (
    <>
      <Nav />
      <main className="shell">
        <div className="pageHead">
          <h1>今日の状況</h1>
          <span className="sub">{today}</span>
        </div>

        {error && <p className="error">{error}</p>}

        {!goal && !error && (
          <p className="notice">
            目標が未設定です。<Link href="/setup">目標設定</Link> で身体情報を入力すると、
            基礎代謝から目標カロリーを算出できます。
          </p>
        )}

        {/* --- 数値サマリ --- */}
        <div className="tiles" style={{ marginBottom: 16 }}>
          <div className="tile">
            <span className="k">摂取</span>
            <span className="v">
              {total ? Math.round(total.kcal) : "—"}
              <span className="u"> kcal</span>
            </span>
          </div>
          <div className="tile">
            <span className="k">目標</span>
            <span className="v">
              {goal ? goal.targetKcal : "—"}
              <span className="u"> kcal</span>
            </span>
          </div>
          <div className={`tile${overKcal ? " over" : ""}`}>
            <span className="k">{overKcal ? "超過" : "残り"}</span>
            <span className="v">
              {remaining ? Math.abs(Math.round(remaining.kcal)) : "—"}
              <span className="u"> kcal</span>
            </span>
          </div>
          <div className="tile">
            <span className="k">体重</span>
            <span className="v">
              {summary?.weightKg != null ? summary.weightKg : "—"}
              <span className="u"> kg</span>
            </span>
          </div>
        </div>

        <div className="grid grid2">
          {/* --- PFC --- */}
          <section className="card">
            <h2>PFC バランス</h2>
            {total ? (
              <div className="bars">
                <NutrientBar
                  label="カロリー"
                  kind="kcal"
                  value={total.kcal}
                  target={goal?.targetKcal ?? null}
                  unit="kcal"
                />
                <NutrientBar
                  label="たんぱく質"
                  kind="protein"
                  value={total.proteinG}
                  target={goal?.targetProteinG ?? null}
                  unit="g"
                />
                <NutrientBar
                  label="脂質"
                  kind="fat"
                  value={total.fatG}
                  target={goal?.targetFatG ?? null}
                  unit="g"
                />
                <NutrientBar
                  label="炭水化物"
                  kind="carb"
                  value={total.carbG}
                  target={goal?.targetCarbG ?? null}
                  unit="g"
                />
              </div>
            ) : (
              <p className="muted">読み込み中…</p>
            )}
          </section>

          {/* --- 体重入力 --- */}
          <section className="card">
            <h2>今日の体重</h2>
            <form className="row" onSubmit={saveWeight}>
              <label>
                体重（kg）
                <input
                  type="number"
                  step="0.1"
                  min="20"
                  max="300"
                  value={weightInput}
                  onChange={(e) => setWeightInput(e.target.value)}
                  required
                />
              </label>
              <button type="submit" disabled={savingWeight}>
                {savingWeight ? "保存中…" : "記録する"}
              </button>
            </form>
            <p className="muted">
              同じ日に再度記録すると上書きされます。グラフには実測値と 7 日移動平均を重ねて表示します。
            </p>
          </section>
        </div>

        {/* --- 食事区分ごとの内訳 --- */}
        <section className="card" style={{ marginTop: 16 }}>
          <h2>食事区分ごとの内訳</h2>
          {summary && summary.byMealType.length > 0 ? (
            <div className="list">
              {summary.byMealType.map((entry) => (
                <div className="listItem" key={entry.mealType}>
                  <span className="name">{MEAL_TYPE_LABELS[entry.mealType]}</span>
                  <span className="meta num">
                    {Math.round(entry.total.kcal)} kcal
                  </span>
                  <span className="meta num">
                    P {entry.total.proteinG.toFixed(1)} / F {entry.total.fatG.toFixed(1)} / C{" "}
                    {entry.total.carbG.toFixed(1)}
                  </span>
                </div>
              ))}
            </div>
          ) : (
            <p className="muted">
              まだ記録がありません。<Link href="/record">食事を記録</Link> から追加してください。
            </p>
          )}
        </section>

        {/* --- グラフ --- */}
        <section className="card" style={{ marginTop: 16 }}>
          <h2>摂取カロリーの推移（直近 {TREND_DAYS} 日）</h2>
          {range ? <KcalChart days={range.days} /> : <p className="muted">読み込み中…</p>}
        </section>

        <section className="card" style={{ marginTop: 16 }}>
          <h2>体重の推移（直近 {TREND_DAYS} 日）</h2>
          {range ? <WeightChart days={range.days} /> : <p className="muted">読み込み中…</p>}
        </section>
      </main>
    </>
  );
}

/**
 * 目標に対する達成度のバー。
 * 目標が未設定の場合は割合を出せないので、摂取量だけを表示する。
 */
function NutrientBar({
  label,
  kind,
  value,
  target,
  unit,
}: {
  label: string;
  kind: "kcal" | "protein" | "fat" | "carb";
  value: number;
  target: number | null;
  unit: string;
}) {
  const ratio = target && target > 0 ? value / target : 0;
  const over = ratio > 1;
  const width = Math.min(100, ratio * 100);

  return (
    <div className="bar">
      <span>{label}</span>
      <span className="track">
        <span
          className={`fill ${over ? "over" : kind}`}
          style={{ width: `${width}%` }}
        />
      </span>
      <span className="amt">
        {value.toFixed(unit === "kcal" ? 0 : 1)}
        {target != null ? ` / ${target.toFixed(unit === "kcal" ? 0 : 1)}` : ""} {unit}
      </span>
    </div>
  );
}
