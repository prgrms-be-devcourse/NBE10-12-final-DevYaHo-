const ACCESS_TOKEN_KEY = "wb.accessToken";
const DEVICE_ID_KEY = "wb.deviceId";

export type StoredTokens = {
  accessToken: string;
};

function isBrowser() {
  return typeof window !== "undefined";
}

export function getAccessToken(): string | null {
  if (!isBrowser()) return null;
  return window.localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getDeviceId(): string | null {
  if (!isBrowser()) return null;
  return window.localStorage.getItem(DEVICE_ID_KEY);
}

export function saveTokens(tokens: StoredTokens): void {
  if (!isBrowser()) return;
  window.localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
}

export function saveDeviceId(deviceId: string): void {
  if (!isBrowser()) return;
  window.localStorage.setItem(DEVICE_ID_KEY, deviceId);
}

export function clearTokens(): void {
  if (!isBrowser()) return;
  window.localStorage.removeItem(ACCESS_TOKEN_KEY);
}
