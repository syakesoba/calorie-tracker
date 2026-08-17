import * as SecureStore from "expo-secure-store";
import type {
  ApiError,
  AuthUser,
  DailySummary,
  Food,
  Goal,
  MealLog,
  MealType,
  TokenResponse,
} from "./types";

/**
 * API のベース URL。
 *
 * Expo Go の実機は開発 PC の LAN IP へアクセスするため、`localhost` では届かない。
 * `.env` の `EXPO_PUBLIC_API_BASE_URL` に PC の IP を入れて使う。
 *
 * **ここを 8080 と 8081 で切り替えれば、同じアプリが Java 実装と Kotlin 実装の
 * どちらにも繋がる。** 2 実装が同じ契約を満たしていることの確認手段として使える。
 */
const BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

const ACCESS_TOKEN_KEY = "calorie_accessToken";
const REFRESH_TOKEN_KEY = "calorie_refreshToken";

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

/**
 * トークンの保存先。
 *
 * Web 版は localStorage だが、こちらは expo-secure-store（iOS のキーチェーン）を使う。
 * 保存先が違うため、Phase 2 では Web と API クライアントを共有していない。
 *
 * SecureStore は非同期なので、同期的に読める Web 版とインターフェースが揃わない。
 * 起動時に一度読んでメモリに載せ、以降はそれを使う。
 */
let accessTokenCache: string | null = null;
let refreshTokenCache: string | null = null;

export const tokenStore = {
  async load(): Promise<void> {
    accessTokenCache = await SecureStore.getItemAsync(ACCESS_TOKEN_KEY);
    refreshTokenCache = await SecureStore.getItemAsync(REFRESH_TOKEN_KEY);
  },
  get access(): string | null {
    return accessTokenCache;
  },
  get refresh(): string | null {
    return refreshTokenCache;
  },
  async save(tokens: TokenResponse): Promise<void> {
    accessTokenCache = tokens.accessToken;
    refreshTokenCache = tokens.refreshToken;
    await SecureStore.setItemAsync(ACCESS_TOKEN_KEY, tokens.accessToken);
    await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, tokens.refreshToken);
  },
  async clear(): Promise<void> {
    accessTokenCache = null;
    refreshTokenCache = null;
    await SecureStore.deleteItemAsync(ACCESS_TOKEN_KEY);
    await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
  },
};

interface RequestOptions {
  method?: string;
  body?: unknown;
  query?: Record<string, string | number | undefined>;
  auth?: boolean;
}

function buildUrl(path: string, query?: RequestOptions["query"]): string {
  let url = BASE_URL + path;
  if (query) {
    const params = Object.entries(query)
      .filter(([, value]) => value !== undefined)
      .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
    if (params.length > 0) url += `?${params.join("&")}`;
  }
  return url;
}

/**
 * リフレッシュ中に他のリクエストが重なった場合、同じ Promise を待たせる。
 * 画面が複数の API を並列で呼ぶため、トークン失効時にリフレッシュが同時多発すると
 * ローテーションで互いを失効させてしまう。
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
          await tokenStore.clear();
          return false;
        }
        await tokenStore.save((await response.json()) as TokenResponse);
        return true;
      } catch {
        // ネットワーク到達不能ではトークンを捨てない。
        // サーバーが一時的に落ちているだけでログアウト扱いにするのは正しくない。
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
    if (auth && tokenStore.access) headers["Authorization"] = `Bearer ${tokenStore.access}`;
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
    throw new ApiRequestError(0, {
      code: "NETWORK_ERROR",
      message:
        "サーバーに接続できません。PC と同じ Wi-Fi にいるか、" +
        "EXPO_PUBLIC_API_BASE_URL が PC の IP になっているか確認してください。",
    });
  }

  if (response.status === 401 && auth && (await refreshTokens())) {
    response = await send();
  }

  if (response.status === 204) {
    return undefined as T;
  }
  if (!response.ok) {
    let parsed: ApiError | null = null;
    try {
      parsed = (await response.json()) as ApiError;
    } catch {
      parsed = null;
    }
    throw new ApiRequestError(response.status, parsed);
  }
  return (await response.json()) as T;
}

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

  currentGoal: () => request<Goal>("/goals/current"),

  putBodyRecord: (date: string, weightKg: number) =>
    request<unknown>(`/body-records/${date}`, { method: "PUT", body: { weightKg } }),

  mealLogs: (date: string) => request<MealLog[]>("/meal-logs", { query: { date } }),

  createMealLog: (body: {
    eatenOn: string;
    mealType: MealType;
    items: { foodId: number; amountG: number }[];
  }) => request<MealLog>("/meal-logs", { method: "POST", body }),

  deleteMealLog: (id: number) => request<void>(`/meal-logs/${id}`, { method: "DELETE" }),

  dailySummary: (date: string) =>
    request<DailySummary>("/summaries/daily", { query: { date } }),
};

/** ISO 形式（yyyy-MM-dd）のローカル日付。UTC 変換で日付がずれないよう手で組む。 */
export function isoDate(date: Date = new Date()): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

/** 接続先を画面に出して、どちらの実装に繋がっているか分かるようにする。 */
export const apiBaseUrl = BASE_URL;
