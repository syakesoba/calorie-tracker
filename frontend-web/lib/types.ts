/**
 * API の型定義。
 *
 * 出所は `api/openapi.yaml`。Phase 2 で openapi-generator による自動生成に
 * 置き換える予定であり、そのときこのファイルは削除される。
 * 現時点で手書きしているのは、生成の仕組みを組む前に MVP を通すため。
 */

export type MealType = "BREAKFAST" | "LUNCH" | "DINNER" | "SNACK";

export const MEAL_TYPE_LABELS: Record<MealType, string> = {
  BREAKFAST: "朝食",
  LUNCH: "昼食",
  DINNER: "夕食",
  SNACK: "間食",
};

export const MEAL_TYPES: MealType[] = ["BREAKFAST", "LUNCH", "DINNER", "SNACK"];

export type FoodSource = "SEED" | "MEXT" | "OFF" | "USER";

export const FOOD_SOURCE_LABELS: Record<FoodSource, string> = {
  SEED: "代表値",
  MEXT: "成分表",
  OFF: "商品DB",
  USER: "自分",
};

export type Sex = "MALE" | "FEMALE";

export type ActivityLevel =
  | "SEDENTARY"
  | "LIGHT"
  | "MODERATE"
  | "ACTIVE"
  | "VERY_ACTIVE";

export const ACTIVITY_LEVEL_LABELS: Record<ActivityLevel, string> = {
  SEDENTARY: "ほぼ座位",
  LIGHT: "軽い運動（週1〜3回）",
  MODERATE: "中程度（週3〜5回）",
  ACTIVE: "激しい（週6〜7回）",
  VERY_ACTIVE: "非常に激しい",
};

/** 栄養値。任意項目はデータが無い場合 null になる。 */
export interface Nutrition {
  kcal: number;
  proteinG: number;
  fatG: number;
  carbG: number;
  saltG: number | null;
  fiberG: number | null;
  sugarG: number | null;
}

export interface Food {
  id: number;
  source: FoodSource;
  name: string;
  nameKana: string | null;
  category: string | null;
  nutritionPer100g: Nutrition;
}

export interface AuthUser {
  id: number;
  email: string;
  displayName: string;
  createdAt: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface Profile {
  sex: Sex;
  birthDate: string;
  heightCm: number;
  activityLevel: ActivityLevel;
  age: number;
}

export interface GoalSuggestion {
  bmr: number;
  tdee: number;
  targetKcal: number;
  targetProteinG: number;
  targetFatG: number;
  targetCarbG: number;
  paceKgPerMonth: number;
  /** true なら、指定ペースでは目標が基礎代謝を下回るため打ち切られている。 */
  cappedAtBmr: boolean;
  basedOnWeightKg: number;
}

export interface Goal {
  id: number;
  startOn: string;
  targetKcal: number;
  targetProteinG: number;
  targetFatG: number;
  targetCarbG: number;
  paceKgPerMonth: number | null;
}

export interface BodyRecord {
  recordedOn: string;
  weightKg: number;
  bodyFatPct: number | null;
}

export interface MealLogItem {
  id: number;
  foodId: number | null;
  foodName: string;
  amountG: number;
  nutrition: Nutrition;
}

export interface MealLog {
  id: number;
  eatenOn: string;
  mealType: MealType;
  note: string | null;
  items: MealLogItem[];
  total: Nutrition;
}

export interface MealTypeTotal {
  mealType: MealType;
  total: Nutrition;
}

export interface DailySummary {
  date: string;
  total: Nutrition;
  byMealType: MealTypeTotal[];
  goal: Goal | null;
  /** 目標 − 摂取。超過している場合は負の値。目標未設定なら null。 */
  remaining: Nutrition | null;
  weightKg: number | null;
}

export interface DailyPoint {
  date: string;
  kcal: number;
  proteinG: number;
  fatG: number;
  carbG: number;
  targetKcal: number | null;
  weightKg: number | null;
  weightMovingAvgKg: number | null;
}

export interface RangeSummary {
  from: string;
  to: string;
  days: DailyPoint[];
}

export interface FieldError {
  field: string;
  message: string;
}

export interface ApiError {
  code: string;
  message: string;
  errors?: FieldError[];
}
