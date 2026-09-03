import { http } from "@/lib/api/http";
import type {
  BillingKeyAuthRequestResponse,
  BillingKeyResponse,
} from "@/lib/api/types";

// 카드 등록 창에 넘길 customerKey를 받아온다 (이미 등록한 적이 있으면 같은 값이 다시 온다)
export async function requestBillingKeyCustomerKey(): Promise<BillingKeyAuthRequestResponse> {
  return http.post<BillingKeyAuthRequestResponse>(
    "/api/payments/billing-key/auth-request",
    undefined,
    { auth: true },
  );
}

// 토스 결제창이 successUrl로 돌려준 authKey를 서버에 넘겨 빌링키로 교환한다
export async function registerBillingKey(
  authKey: string,
  customerKey: string,
): Promise<BillingKeyResponse> {
  return http.post<BillingKeyResponse>(
    "/api/payments/billing-key",
    { authKey, customerKey },
    { auth: true },
  );
}

export async function getBillingKey(): Promise<BillingKeyResponse> {
  return http.get<BillingKeyResponse>("/api/payments/billing-key", { auth: true });
}

export async function deleteBillingKey(): Promise<void> {
  return http.delete<void>("/api/payments/billing-key", { auth: true });
}
