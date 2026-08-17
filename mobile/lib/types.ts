/**
 * API の型定義。出所は `api/openapi.yaml`。
 *
 * frontend-web に似た内容をあえて重複させている。Phase 2 の時点で共通パッケージに
 * しないのは、Phase 5 で openapi-generator による自動生成へ置き換える予定であり、
 * いま共通化すると捨てる予定のものに設計コストを払うことになるため。
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

export interface Goal {
  id: number;
  startOn: string;
  targetKcal: number;
  targetProteinG: number;
  targetFatG: number;
  targetCarbG: number;
  paceKgPerMonth: number | null;
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

export interface FieldError {
  field: string;
  message: string;
}

export interface ApiError {
  code: string;
  message: string;
  errors?: FieldError[];
}
