"use client";

import { useEffect, useState } from "react";
import { Nav } from "@/components/Nav";
import { ApiRequestError, api, isoDate } from "@/lib/api";
import { useRequireAuth } from "@/lib/auth";
import {
  ACTIVITY_LEVEL_LABELS,
  type ActivityLevel,
  type Goal,
  type GoalSuggestion,
  type Sex,
} from "@/lib/types";

const ACTIVITY_LEVELS = Object.keys(ACTIVITY_LEVEL_LABELS) as ActivityLevel[];

export default function SetupPage() {
  const waiting = useRequireAuth();

  const [sex, setSex] = useState<Sex>("MALE");
  const [birthDate, setBirthDate] = useState("1990-01-01");
  const [heightCm, setHeightCm] = useState("170");
  const [activityLevel, setActivityLevel] = useState<ActivityLevel>("LIGHT");
  const [weightKg, setWeightKg] = useState("");
  const [pace, setPace] = useState("2");

  const [suggestion, setSuggestion] = useState<GoalSuggestion | null>(null);
  const [savedGoal, setSavedGoal] = useState<Goal | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // 既存の設定があれば読み込んでフォームに入れる。無い場合は 404 なので黙って無視する。
  useEffect(() => {
    if (waiting) return;
    (async () => {
      try {
        const profile = await api.getProfile();
        setSex(profile.sex);
        setBirthDate(profile.birthDate);
        setHeightCm(String(profile.heightCm));
        setActivityLevel(profile.activityLevel);
      } catch {
        // プロフィール未設定。初期値のままでよい。
      }
      try {
        setSavedGoal(await api.currentGoal());
      } catch {
        // 目標未設定。
      }
    })();
  }, [waiting]);

  /** プロフィールと体重を保存し、続けて目標を算出する。 */
  const calculate = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await api.putProfile({
        sex,
        birthDate,
        heightCm: Number(heightCm),
        activityLevel,
      });

      // 目標算出には体重が要る。入力されていればここで記録する。
      if (weightKg.trim().length > 0) {
        await api.putBodyRecord(isoDate(), Number(weightKg));
      }

      setSuggestion(await api.goalSuggestion(Number(pace)));
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setError(e.fieldErrors.length > 0 ? e.fieldErrors[0].message : e.message);
      } else {
        setError("算出に失敗しました。");
      }
    } finally {
      setBusy(false);
    }
  };

  const saveGoal = async () => {
    if (!suggestion) return;
    setBusy(true);
    setError(null);
    try {
      setSavedGoal(
        await api.putGoal({
          targetKcal: suggestion.targetKcal,
          targetProteinG: suggestion.targetProteinG,
          targetFatG: suggestion.targetFatG,
          targetCarbG: suggestion.targetCarbG,
          paceKgPerMonth: suggestion.paceKgPerMonth,
        }),
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "保存に失敗しました。");
    } finally {
      setBusy(false);
    }
  };

  if (waiting) {
    return (
      <main className="shell">
        <p className="muted">読み込み中…</p>
      </main>
    );
  }

  return (
    <>
      <Nav />
      <main className="shell">
        <div className="pageHead">
          <h1>目標設定</h1>
          <span className="sub">Mifflin-St Jeor 式</span>
        </div>

        {error && <p className="error">{error}</p>}

        {savedGoal && (
          <p className="notice num">
            現在の目標 {savedGoal.targetKcal} kcal（{savedGoal.startOn} から）
            — P {savedGoal.targetProteinG} / F {savedGoal.targetFatG} / C {savedGoal.targetCarbG} g
          </p>
        )}

        <form className="card" onSubmit={calculate}>
          <h2>身体情報</h2>

          <div className="row">
            <label>
              性別
              <select value={sex} onChange={(e) => setSex(e.target.value as Sex)}>
                <option value="MALE">男性</option>
                <option value="FEMALE">女性</option>
              </select>
            </label>
            <label>
              生年月日
              <input
                type="date"
                value={birthDate}
                onChange={(e) => setBirthDate(e.target.value)}
                required
              />
            </label>
          </div>

          <div className="row">
            <label>
              身長（cm）
              <input
                type="number"
                step="0.1"
                min="50"
                max="250"
                value={heightCm}
                onChange={(e) => setHeightCm(e.target.value)}
                required
              />
            </label>
            <label>
              体重（kg）
              <input
                type="number"
                step="0.1"
                min="20"
                max="300"
                value={weightKg}
                onChange={(e) => setWeightKg(e.target.value)}
                placeholder="今日の体重"
              />
            </label>
          </div>

          <label>
            活動レベル
            <select
              value={activityLevel}
              onChange={(e) => setActivityLevel(e.target.value as ActivityLevel)}
            >
              {ACTIVITY_LEVELS.map((level) => (
                <option key={level} value={level}>
                  {ACTIVITY_LEVEL_LABELS[level]}
                </option>
              ))}
            </select>
          </label>

          <label>
            減量ペース（1 か月あたり kg）
            <input
              type="number"
              step="0.5"
              min="0"
              max="4"
              value={pace}
              onChange={(e) => setPace(e.target.value)}
            />
            <span className="muted">0 なら体重維持</span>
          </label>

          <button type="submit" disabled={busy}>
            {busy ? "計算中…" : "目標を算出する"}
          </button>
          <p className="muted">
            体重は日々変わるため、算出には体重記録の最新値を使います。
            この式は推定であり、健康上の判断に用いるものではありません。
          </p>
        </form>

        {suggestion && (
          <section className="card" style={{ marginTop: 16 }}>
            <h2>算出結果</h2>

            {suggestion.cappedAtBmr && (
              <p className="error">
                このペースでは目標が基礎代謝（{suggestion.bmr} kcal）を下回るため、
                基礎代謝で打ち切っています。ペースを緩めることを検討してください。
              </p>
            )}

            <div className="tiles">
              <div className="tile">
                <span className="k">基礎代謝 BMR</span>
                <span className="v">
                  {suggestion.bmr}
                  <span className="u"> kcal</span>
                </span>
              </div>
              <div className="tile">
                <span className="k">総消費 TDEE</span>
                <span className="v">
                  {suggestion.tdee}
                  <span className="u"> kcal</span>
                </span>
              </div>
              <div className="tile">
                <span className="k">目標摂取</span>
                <span className="v">
                  {suggestion.targetKcal}
                  <span className="u"> kcal</span>
                </span>
              </div>
            </div>

            <p className="muted num">
              目標 PFC — たんぱく質 {suggestion.targetProteinG} g / 脂質 {suggestion.targetFatG} g /
              炭水化物 {suggestion.targetCarbG} g（体重 {suggestion.basedOnWeightKg} kg で算出）
            </p>

            <button type="button" onClick={saveGoal} disabled={busy}>
              {busy ? "保存中…" : "この目標を採用する"}
            </button>
          </section>
        )}
      </main>
    </>
  );
}
