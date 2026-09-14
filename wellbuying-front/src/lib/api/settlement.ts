import { http } from "@/lib/api/http";
import type {
  PageResponse,
  SettlementListItemResponse,
  SettlementListStatus,
  SettlementMonthlySummaryResponse,
  SettlementParticipantResponse,
  SettlementProgressResponse,
  SettlementTrendGranularity,
  SettlementTrendPointResponse,
} from "@/lib/api/types";

// 판매자 본인의 정산 내역 - 월별(공동구매 성사월 기준) + 상태 필터(PENDING/COMPLETED).
// year/month 둘 중 하나라도 생략하면 이번 달, status 생략 시 대기중+완료 전체
export function listMySettlements(params?: {
  year?: number;
  month?: number;
  status?: SettlementListStatus;
  page?: number;
  size?: number;
}): Promise<PageResponse<SettlementListItemResponse>> {
  const query = new URLSearchParams();
  if (params?.year !== undefined) query.set("year", String(params.year));
  if (params?.month !== undefined) query.set("month", String(params.month));
  if (params?.status !== undefined) query.set("status", params.status);
  if (params?.page !== undefined) query.set("page", String(params.page));
  if (params?.size !== undefined) query.set("size", String(params.size));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return http.get<PageResponse<SettlementListItemResponse>>(`/api/settlements/me${suffix}`, { auth: true });
}

// 매출 추이 그래프 - 이번 달 포함 최근 12개월(기본 월 단위)
export function getSettlementTrend(
  granularity: SettlementTrendGranularity = "MONTHLY",
): Promise<SettlementTrendPointResponse[]> {
  return http.get<SettlementTrendPointResponse[]>(`/api/settlements/me/trend?granularity=${granularity}`, {
    auth: true,
  });
}

// 이번 달 요약 카드 3개
export function getSettlementMonthlySummary(): Promise<SettlementMonthlySummaryResponse> {
  return http.get<SettlementMonthlySummaryResponse>("/api/settlements/me/monthly-summary", { auth: true });
}

// PENDING 건 상세 - 결제 진행도
export function getSettlementProgress(groupBuyId: number): Promise<SettlementProgressResponse> {
  return http.get<SettlementProgressResponse>(`/api/settlements/me/${groupBuyId}/progress`, { auth: true });
}

// COMPLETED 건 상세 - 결제한 참여자 명단
export function getSettlementParticipants(groupBuyId: number): Promise<SettlementParticipantResponse[]> {
  return http.get<SettlementParticipantResponse[]>(`/api/settlements/me/${groupBuyId}/participants`, {
    auth: true,
  });
}
