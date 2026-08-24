import { http } from "@/lib/api/http";
import { getDeviceId, saveDeviceId, saveTokens, clearTokens } from "@/lib/auth/token-storage";
import type {
  LoginResponse,
  MemberResponse,
  SellerApplyRequest,
  SellerSignupRequest,
  SignupRequest,
  SignupResponse,
} from "@/lib/api/types";

export async function login(email: string, password: string): Promise<LoginResponse> {
  const deviceId = getDeviceId();
  const response = await http.post<LoginResponse>(
    "/api/auth/login",
    { email, password },
    { headers: deviceId ? { "X-Device-Id": deviceId } : {} },
  );
  saveTokens({ accessToken: response.accessToken, refreshToken: response.refreshToken });
  saveDeviceId(response.deviceId);
  return response;
}

export async function logout(): Promise<void> {
  try {
    await http.post<void>("/api/auth/logout", undefined, { auth: true });
  } finally {
    clearTokens();
  }
}

export async function logoutAll(): Promise<void> {
  try {
    await http.post<void>("/api/auth/logout-all", undefined, { auth: true });
  } finally {
    clearTokens();
  }
}

export function sendVerificationCode(email: string): Promise<void> {
  return http.post<void>("/api/auth/email/verification-code", { email });
}

export function verifyEmail(email: string, code: string): Promise<void> {
  return http.post<void>("/api/auth/email/verify", { email, code });
}

export function signup(request: SignupRequest): Promise<SignupResponse> {
  return http.post<SignupResponse>("/api/auth/signup", request);
}

export function sellerSignup(request: SellerSignupRequest): Promise<SignupResponse> {
  return http.post<SignupResponse>("/api/auth/seller/signup", request);
}

export function sellerApply(request: SellerApplyRequest): Promise<void> {
  return http.post<void>("/api/auth/seller/apply", request, { auth: true });
}

export function getMe(): Promise<MemberResponse> {
  return http.get<MemberResponse>("/api/members/me", { auth: true });
}
