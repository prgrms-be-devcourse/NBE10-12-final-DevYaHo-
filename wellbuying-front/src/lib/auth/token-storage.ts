import type { DeviceSessionResponse } from "@/lib/api/types";

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

const DEVICES_CACHE_KEY = "wb.devicesCache";

export function getCachedDevices(): DeviceSessionResponse[] | null {
  if (!isBrowser()) return null;
  const data = window.localStorage.getItem(DEVICES_CACHE_KEY);
  if (!data) return null;
  try {
    return JSON.parse(data);
  } catch {
    return null;
  }
}

export function saveCachedDevices(devices: DeviceSessionResponse[]): void {
  if (!isBrowser()) return;
  window.localStorage.setItem(DEVICES_CACHE_KEY, JSON.stringify(devices));
}
