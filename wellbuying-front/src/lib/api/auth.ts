import { http, API_BASE_URL } from "@/lib/api/http";
import { getDeviceId, saveDeviceId, saveTokens, clearTokens } from "@/lib/auth/token-storage";
import type {
  DeviceSessionResponse,
  LoginResponse,
  MemberResponse,
  OAuthProvider,
  SellerApplyRequest,
  SellerSignupRequest,
  SignupRequest,
  SignupResponse,
  SocialAccountsResponse,
  SocialLinkResponse,
  UpdateMemberRequest,
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

export function updateProfile(request: UpdateMemberRequest): Promise<MemberResponse> {
  return http.patch<MemberResponse>("/api/members/me", request, { auth: true });
}

export function withdraw(): Promise<void> {
  return http.delete<void>("/api/members/me", { auth: true });
}

// 백엔드 표준 Spring Security 진입점 - 이동만 하면 되고 콜백은 서버가 처리한다
export function getOAuthAuthorizationUrl(provider: OAuthProvider): string {
  return `${API_BASE_URL}/oauth2/authorization/${provider.toLowerCase()}`;
}

export async function exchangeOAuthCode(code: string): Promise<LoginResponse> {
  const response = await http.post<LoginResponse>("/api/auth/oauth/exchange", { code });
  saveTokens({ accessToken: response.accessToken, refreshToken: response.refreshToken });
  saveDeviceId(response.deviceId);
  return response;
}

export function getSocialAccounts(): Promise<SocialAccountsResponse> {
  return http.get<SocialAccountsResponse>("/api/members/me/social-accounts", { auth: true });
}

export function issueLinkUrl(provider: OAuthProvider): Promise<SocialLinkResponse> {
  return http.post<SocialLinkResponse>(
    `/api/members/me/social-accounts/${provider.toLowerCase()}`,
    undefined,
    { auth: true },
  );
}

export function unlinkProvider(provider: OAuthProvider): Promise<void> {
  return http.delete<void>(`/api/members/me/social-accounts/${provider.toLowerCase()}`, { auth: true });
}

export function getDevices(): Promise<DeviceSessionResponse[]> {
  return http.get<DeviceSessionResponse[]>("/api/auth/devices", { auth: true });
}
