import { http } from "@/lib/api/http";
import type { PageResponse, SettlementResponse } from "@/lib/api/types";

// 판매자 본인의 정산 내역 목록 (최신 확정순). 본인 것만 반환된다
export function listMySettlements(params?: {
  page?: number;
  size?: number;
}): Promise<PageResponse<SettlementResponse>> {
  const query = new URLSearchParams();
  if (params?.page !== undefined) query.set("page", String(params.page));
  if (params?.size !== undefined) query.set("size", String(params.size));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return http.get<PageResponse<SettlementResponse>>(`/api/settlements/me${suffix}`, { auth: true });
}
