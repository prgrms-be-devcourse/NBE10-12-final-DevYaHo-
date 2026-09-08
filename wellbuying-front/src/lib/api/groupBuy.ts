import { http } from "@/lib/api/http";
import type {
  GroupBuyCreateRequest,
  GroupBuyDetailResponse,
  GroupBuyPartCreateRequest,
  GroupBuyPartMeResponse,
  GroupBuyPartResponse,
  GroupBuyPriceTier,
  GroupBuyStatus,
  GroupBuyStatusResponse,
  GroupBuySummaryResponse,
  GroupBuySuspensionRequestCreateRequest,
  GroupBuyUpdateRequest,
  PageResponse,
} from "@/lib/api/types";

export function listGroupBuys(params?: {
  status?: GroupBuyStatus;
  page?: number;
  size?: number;
  // Spring Pageable이 그대로 받는 "속성,방향" 형식(예: "viewCount,desc") - 서버가 이 기준으로
  // 정렬해서 내려주므로, 전체 데이터 중 상위 N개를 잘라 받는 목록에서도 순서가 항상 정확하다
  sort?: string;
}): Promise<PageResponse<GroupBuySummaryResponse>> {
  const query = new URLSearchParams();
  if (params?.status) query.set("status", params.status);
  if (params?.page !== undefined) query.set("page", String(params.page));
  if (params?.size !== undefined) query.set("size", String(params.size));
  if (params?.sort) query.set("sort", params.sort);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return http.get<PageResponse<GroupBuySummaryResponse>>(`/api/groupBuys${suffix}`);
}

export function getGroupBuy(groupBuyId: number): Promise<GroupBuyDetailResponse> {
  return http.get<GroupBuyDetailResponse>(`/api/groupBuys/${groupBuyId}`);
}

export function getGroupBuyStatus(groupBuyId: number): Promise<GroupBuyStatusResponse> {
  return http.get<GroupBuyStatusResponse>(`/api/groupBuys/${groupBuyId}/status`);
}

export function getGroupBuyPriceTiers(groupBuyId: number): Promise<GroupBuyPriceTier[]> {
  return http.get<GroupBuyPriceTier[]>(`/api/groupBuys/${groupBuyId}/price`);
}

export function createGroupBuy(request: GroupBuyCreateRequest): Promise<GroupBuyDetailResponse> {
  return http.post<GroupBuyDetailResponse>("/api/groupBuys", request, { auth: true });
}

export function updateGroupBuy(
  groupBuyId: number,
  request: GroupBuyUpdateRequest,
): Promise<GroupBuyDetailResponse> {
  return http.patch<GroupBuyDetailResponse>(`/api/groupBuys/${groupBuyId}`, request, { auth: true });
}

export function cancelGroupBuy(groupBuyId: number): Promise<void> {
  return http.delete<void>(`/api/groupBuys/${groupBuyId}`, { auth: true });
}

export function participateInGroupBuy(
  groupBuyId: number,
  request: GroupBuyPartCreateRequest,
): Promise<GroupBuyPartResponse> {
  return http.post<GroupBuyPartResponse>(`/api/groupBuys/${groupBuyId}/part`, request, { auth: true });
}

export function cancelGroupBuyParticipation(groupBuyId: number, partId: number): Promise<void> {
  return http.delete<void>(`/api/groupBuys/${groupBuyId}/part/${partId}`, { auth: true });
}

export function getMyGroupBuyParticipation(groupBuyId: number): Promise<GroupBuyPartMeResponse> {
  return http.get<GroupBuyPartMeResponse>(`/api/groupBuys/${groupBuyId}/part/me`, { auth: true });
}

export function listMyGroupBuys(params?: {
  status?: GroupBuyStatus;
  page?: number;
  size?: number;
}): Promise<PageResponse<GroupBuySummaryResponse>> {
  const query = new URLSearchParams();
  if (params?.status) query.set("status", params.status);
  if (params?.page !== undefined) query.set("page", String(params.page));
  if (params?.size !== undefined) query.set("size", String(params.size));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return http.get<PageResponse<GroupBuySummaryResponse>>(`/api/groupBuys/mine${suffix}`, { auth: true });
}

export function requestGroupBuySuspension(
  groupBuyId: number,
  request: GroupBuySuspensionRequestCreateRequest,
): Promise<void> {
  return http.post<void>(`/api/groupBuys/${groupBuyId}/suspension-requests`, request, { auth: true });
}
