import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  saveTokens,
} from "@/lib/auth/token-storage";
import type { ErrorResponse, ReissueResponse } from "@/lib/api/types";

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  code: string;
  status: number;

  constructor(status: number, error: ErrorResponse) {
    super(error.message);
    this.name = "ApiError";
    this.code = error.code;
    this.status = status;
  }
}

type RequestOptions = {
  method?: string;
  body?: unknown;
  headers?: Record<string, string>;
  auth?: boolean;
};

// 동시에 여러 요청이 401을 받아도 reissue는 한 번만 실행되도록 진행 중인 Promise를 공유
let refreshing: Promise<boolean> | null = null;

async function reissueTokens(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;

  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/reissue`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!response.ok) return false;
    const data: ReissueResponse = await response.json();
    saveTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken });
    return true;
  } catch {
    return false;
  }
}

async function parseError(response: Response): Promise<ApiError> {
  try {
    const data: ErrorResponse = await response.json();
    return new ApiError(response.status, data);
  } catch {
    return new ApiError(response.status, {
      code: "UNKNOWN",
      message: response.statusText || "요청 처리 중 오류가 발생했습니다.",
    });
  }
}

async function request<T>(
  path: string,
  { method = "GET", body, headers = {}, auth = false }: RequestOptions = {},
  retryOn401 = true,
): Promise<T> {
  const finalHeaders: Record<string, string> = {
    "Content-Type": "application/json",
    ...headers,
  };

  if (auth) {
    const accessToken = getAccessToken();
    if (accessToken) finalHeaders.Authorization = `Bearer ${accessToken}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: finalHeaders,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.ok) {
    if (response.status === 204) return undefined as T;
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }

  const error = await parseError(response);

  if (auth && error.code === "AUTH_401_EXPIRED" && retryOn401) {
    if (!refreshing) {
      refreshing = reissueTokens().finally(() => {
        refreshing = null;
      });
    }
    const refreshed = await refreshing;
    if (refreshed) {
      return request<T>(path, { method, body, headers, auth }, false);
    }
    clearTokens();
  }

  throw error;
}

export const http = {
  get: <T>(path: string, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(path, { ...options, method: "POST", body }),
  patch: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(path, { ...options, method: "PATCH", body }),
  delete: <T>(path: string, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(path, { ...options, method: "DELETE" }),
};
