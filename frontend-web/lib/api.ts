import type {
  ApiError,
  BodyRecord,
  DailySummary,
  Food,
  Goal,
  GoalSuggestion,
  MealLog,
  MealType,
  Profile,
  RangeSummary,
  TokenResponse,
  AuthUser,
} from "./types";

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

const ACCESS_TOKEN_KEY = "calorie.accessToken";
const REFRESH_TOKEN_KEY = "calorie.refreshToken";

/**
 * API 呼び出しが失敗したことを表す例外。
 *
 * サーバーの `ApiError` をそのまま持つので、画面側は `message` を出せばよく、
 * 分岐が必要なときだけ `code` を見る。
 */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: { field: string; message: string }[];

  constructor(status: number, body: ApiError | null) {
    super(body?.message ?? "通信に失敗しました。");
    this.name = "ApiRequestError";
    this.status = status;
    this.code = body?.code ?? "NETWORK_ERROR";
    this.fieldErrors = body?.errors ?? [];
  }
}

// ---------------------------------------------------------------- トークン保持

/**
 * トークンは localStorage に置く。
 *
 * HttpOnly Cookie の方が XSS には強いが、Phase 2 で同じ API を Expo から叩くため、
 * ブラウザの Cookie に依存しない持ち方に揃えている。学習用途としてこの割り切りを
 * 明示しておく。
 */
export const tokenStore = {
  get access(): string | null {
    if (typeof window === "undefined") return null;
    return window.localStorage.getItem(ACCESS_TOKEN_KEY);
  },
  get refresh(): string | null {
    if (typeof window === "undefined") return null;
    return window.localStorage.getItem(REFRESH_TOKEN_KEY);
  },
  save(tokens: TokenResponse) {
    window.localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    window.localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  },
  clear() {
    window.localStorage.removeItem(ACCESS_TOKEN_KEY);
    window.localStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};

// ------------------------------------------------------------------ 呼び出し

interface RequestOptions {
  method?: string;
  body?: unknown;
  query?: Record<string, string | number | undefined>;
  /** 認証不要のエンドポイント（ログイン等）では false にする。 */
  auth?: boolean;
}

function buildUrl(path: string, query?: RequestOptions["query"]): string {
  const url = new URL(BASE_URL + path);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined) url.searchParams.set(key, String(value));
    }
  }
  return url.toString();
}

async function parseError(response: Response): Promise<ApiError | null> {
  try {
    return (await response.json()) as ApiError;
  } catch {
    return null;
  }
}

/**
 * リフレッシュ中に他のリクエストが重なった場合、同じ Promise を待たせる。
 * ダッシュボードは複数の API を並列で呼ぶので、トークン失効時に
 * リフレッシュが同時多発するとローテーションで互いを失効させてしまう。
 */
let refreshInFlight: Promise<boolean> | null = null;

async function refreshTokens(): Promise<boolean> {
  const refreshToken = tokenStore.refresh;
  if (!refreshToken) return false;

  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const response = await fetch(buildUrl("/auth/refresh"), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken }),
        });
        if (!response.ok) {
          tokenStore.clear();
          return false;
        }
        tokenStore.save((await response.json()) as TokenResponse);
        return true;
      } catch {
        return false;
      } finally {
        refreshInFlight = null;
      }
    })();
  }
  return refreshInFlight;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, query, auth = true } = options;

  const send = async (): Promise<Response> => {
    const headers: Record<string, string> = {};
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (auth) {
      const token = tokenStore.access;
      if (token) headers["Authorization"] = `Bearer ${token}`;
    }
    return fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  };

  let response: Response;
  try {
    response = await send();
  } catch {
    // サーバーが起動していない場合もここに来る。ネットワーク層の失敗として扱う。
    throw new ApiRequestError(0, {
      code: "NETWORK_ERROR",
      message: "サーバーに接続できません。バックエンドが起動しているか確認してください。",
    });
  }

  // アクセストークンが失効していたら 1 度だけ更新して再送する
  if (response.status === 401 && auth && (await refreshTokens())) {
    response = await send();
  }

  if (response.status === 204) {
    return undefined as T;
  }
  if (!response.ok) {
    throw new ApiRequestError(response.status, await parseError(response));
  }
  return (await response.json()) as T;
}

// -------------------------------------------------------------------- API 群

export const api = {
  signUp: (email: string, password: string, displayName: string) =>
    request<TokenResponse>("/auth/signup", {
      method: "POST",
      body: { email, password, displayName },
      auth: false,
    }),

  login: (email: string, password: string) =>
    request<TokenResponse>("/auth/login", {
      method: "POST",
      body: { email, password },
      auth: false,
    }),

  logout: (refreshToken: string) =>
    request<void>("/auth/logout", {
      method: "POST",
      body: { refreshToken },
      auth: false,
    }),

  me: () => request<AuthUser>("/auth/me"),

  searchFoods: (query: string, limit = 20) =>
    request<Food[]>("/foods", { query: { query, limit } }),

  createFood: (body: {
    name: string;
    nameKana?: string;
    category?: string;
    nutritionPer100g: {
      kcal: number;
      proteinG: number;
      fatG: number;
      carbG: number;
      saltG?: number;
    };
  }) => request<Food>("/foods", { method: "POST", body }),

  getProfile: () => request<Profile>("/profile"),

  putProfile: (body: {
    sex: string;
    birthDate: string;
    heightCm: number;
    activityLevel: string;
  }) => request<Profile>("/profile", { method: "PUT", body }),

  goalSuggestion: (paceKgPerMonth: number) =>
    request<GoalSuggestion>("/goals/suggestion", { query: { paceKgPerMonth } }),

  currentGoal: () => request<Goal>("/goals/current"),

  putGoal: (body: {
    targetKcal: number;
    targetProteinG: number;
    targetFatG: number;
    targetCarbG: number;
    paceKgPerMonth?: number;
  }) => request<Goal>("/goals", { method: "PUT", body }),

  putBodyRecord: (date: string, weightKg: number, bodyFatPct?: number) =>
    request<BodyRecord>(`/body-records/${date}`, {
      method: "PUT",
      body: { weightKg, bodyFatPct },
    }),

  mealLogs: (date: string) => request<MealLog[]>("/meal-logs", { query: { date } }),

  createMealLog: (body: {
    eatenOn: string;
    mealType: MealType;
    note?: string;
    items: { foodId: number; amountG: number }[];
  }) => request<MealLog>("/meal-logs", { method: "POST", body }),

  deleteMealLog: (id: number) =>
    request<void>(`/meal-logs/${id}`, { method: "DELETE" }),

  dailySummary: (date: string) =>
    request<DailySummary>("/summaries/daily", { query: { date } }),

  rangeSummary: (from: string, to: string) =>
    request<RangeSummary>("/summaries/range", { query: { from, to } }),
};

/** ISO 形式（yyyy-MM-dd）のローカル日付。UTC 変換で日付がずれないよう手で組む。 */
export function isoDate(date: Date = new Date()): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return isoDate(date);
}
