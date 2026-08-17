"use client";

import type { DailyPoint } from "@/lib/types";

/**
 * 推移グラフ。
 *
 * 外部のチャートライブラリを使わず SVG を直接描いている。理由は 3 つ。
 * CDN を参照しない方針であること、必要な表現が棒・折れ線・面の塗りに限られること、
 * そして Phase 2 で Expo 側へ持っていくときに依存を増やしたくないこと。
 *
 * カロリーと体重は縦軸の単位が違うため、1 つの図に 2 軸を詰めず、
 * 横軸だけを揃えた 2 段構成にしている。2 軸の図は目盛りの対応が読み取りにくい。
 */

const WIDTH = 720;
const HEIGHT = 150;
const PAD_LEFT = 44;
const PAD_RIGHT = 8;
const PAD_TOP = 12;
const PAD_BOTTOM = 20;

const PLOT_WIDTH = WIDTH - PAD_LEFT - PAD_RIGHT;
const PLOT_HEIGHT = HEIGHT - PAD_TOP - PAD_BOTTOM;

/** 横軸の位置。1 点だけのときは中央に置く。 */
function xAt(index: number, count: number): number {
  if (count <= 1) return PAD_LEFT + PLOT_WIDTH / 2;
  return PAD_LEFT + (PLOT_WIDTH * index) / (count - 1);
}

function yAt(value: number, min: number, max: number): number {
  if (max === min) return PAD_TOP + PLOT_HEIGHT / 2;
  return PAD_TOP + PLOT_HEIGHT * (1 - (value - min) / (max - min));
}

/** 横軸ラベル。全日付を出すと潰れるので、両端と中央だけにする。 */
function axisLabels(days: DailyPoint[]): { index: number; text: string }[] {
  if (days.length === 0) return [];
  const format = (iso: string) => {
    const [, month, day] = iso.split("-");
    return `${Number(month)}/${Number(day)}`;
  };
  const indexes = days.length < 3
    ? days.map((_, i) => i)
    : [0, Math.floor((days.length - 1) / 2), days.length - 1];
  return indexes.map((index) => ({ index, text: format(days[index].date) }));
}

function GridAndAxis({
  days,
  min,
  max,
  ticks,
  unit,
}: {
  days: DailyPoint[];
  min: number;
  max: number;
  ticks: number[];
  unit: string;
}) {
  return (
    <>
      {ticks.map((value) => {
        const y = yAt(value, min, max);
        return (
          <g key={value}>
            <line
              x1={PAD_LEFT}
              y1={y}
              x2={WIDTH - PAD_RIGHT}
              y2={y}
              stroke="var(--rule-soft)"
              strokeWidth="1"
            />
            <text
              x={PAD_LEFT - 6}
              y={y + 3.5}
              textAnchor="end"
              fontSize="9"
              fontFamily="var(--f-mono)"
              fill="var(--muted)"
            >
              {value}
            </text>
          </g>
        );
      })}
      <text
        x={PAD_LEFT - 6}
        y={PAD_TOP - 3}
        textAnchor="end"
        fontSize="8"
        fontFamily="var(--f-mono)"
        fill="var(--muted)"
      >
        {unit}
      </text>
      {axisLabels(days).map(({ index, text }) => (
        <text
          key={index}
          x={xAt(index, days.length)}
          y={HEIGHT - 6}
          textAnchor="middle"
          fontSize="9"
          fontFamily="var(--f-mono)"
          fill="var(--muted)"
        >
          {text}
        </text>
      ))}
    </>
  );
}

/** 目盛り値を「切りのよい」間隔で作る。 */
function niceTicks(min: number, max: number, count = 3): number[] {
  if (max <= min) return [min];
  const rawStep = (max - min) / count;
  const magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
  const step = Math.ceil(rawStep / magnitude) * magnitude;
  const ticks: number[] = [];
  for (let value = Math.ceil(min / step) * step; value <= max; value += step) {
    ticks.push(Math.round(value * 100) / 100);
  }
  return ticks;
}

export function KcalChart({ days }: { days: DailyPoint[] }) {
  const targets = days.map((d) => d.targetKcal).filter((v): v is number => v != null);
  const maxValue = Math.max(1, ...days.map((d) => d.kcal), ...targets);
  const max = Math.ceil(maxValue / 500) * 500;
  const barWidth = Math.max(2, Math.min(18, PLOT_WIDTH / Math.max(days.length, 1) - 2));

  // 目標線は日ごとに変わりうる（目標を履歴で持つため）ので、階段状に描く
  const targetPath = days
    .map((day, index) => {
      if (day.targetKcal == null) return null;
      const x = xAt(index, days.length);
      const y = yAt(day.targetKcal, 0, max);
      return `${index === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .filter(Boolean)
    .join(" ");

  return (
    <div>
      <svg
        className="chart"
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label="摂取カロリーの推移"
        preserveAspectRatio="none"
      >
        <GridAndAxis days={days} min={0} max={max} ticks={niceTicks(0, max)} unit="kcal" />
        {days.map((day, index) => {
          const x = xAt(index, days.length);
          const y = yAt(day.kcal, 0, max);
          const over = day.targetKcal != null && day.kcal > day.targetKcal;
          return (
            <rect
              key={day.date}
              x={x - barWidth / 2}
              y={y}
              width={barWidth}
              height={Math.max(0, PAD_TOP + PLOT_HEIGHT - y)}
              fill={over ? "var(--warn)" : "var(--accent)"}
              opacity={day.kcal === 0 ? 0 : 0.85}
            />
          );
        })}
        {targetPath && (
          <path
            d={targetPath}
            fill="none"
            stroke="var(--ink)"
            strokeWidth="1.5"
            strokeDasharray="5 3"
          />
        )}
      </svg>
      <div className="chartLegend">
        <span>
          <i style={{ background: "var(--accent)" }} />
          摂取カロリー
        </span>
        <span>
          <i style={{ background: "var(--warn)" }} />
          目標超過
        </span>
        <span>
          <i style={{ background: "var(--ink)" }} />
          目標
        </span>
      </div>
    </div>
  );
}

export function WeightChart({ days }: { days: DailyPoint[] }) {
  const values = days.flatMap((d) =>
    [d.weightKg, d.weightMovingAvgKg].filter((v): v is number => v != null),
  );

  if (values.length === 0) {
    return <p className="muted">体重の記録がまだありません。</p>;
  }

  // 体重の変動幅は小さいので 0 起点にはしない。前後 1kg の余白を取る。
  const min = Math.floor(Math.min(...values) - 1);
  const max = Math.ceil(Math.max(...values) + 1);

  // 記録が無い日は移動平均が前日値を保つため、線は途切れない
  const avgPath = days
    .map((day, index) => {
      if (day.weightMovingAvgKg == null) return null;
      const x = xAt(index, days.length);
      const y = yAt(day.weightMovingAvgKg, min, max);
      return { x, y };
    })
    .filter((p): p is { x: number; y: number } => p != null)
    .map((p, i) => `${i === 0 ? "M" : "L"}${p.x.toFixed(1)},${p.y.toFixed(1)}`)
    .join(" ");

  return (
    <div>
      <svg
        className="chart"
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label="体重の推移"
        preserveAspectRatio="none"
      >
        <GridAndAxis days={days} min={min} max={max} ticks={niceTicks(min, max)} unit="kg" />
        {avgPath && (
          <path d={avgPath} fill="none" stroke="var(--accent)" strokeWidth="2" />
        )}
        {days.map((day, index) =>
          day.weightKg == null ? null : (
            <circle
              key={day.date}
              cx={xAt(index, days.length)}
              cy={yAt(day.weightKg, min, max)}
              r="2.5"
              fill="var(--muted)"
            />
          ),
        )}
      </svg>
      <div className="chartLegend">
        <span>
          <i style={{ background: "var(--accent)", height: 3 }} />
          7日移動平均
        </span>
        <span>
          <i style={{ background: "var(--muted)", height: 3 }} />
          実測値
        </span>
      </div>
    </div>
  );
}
