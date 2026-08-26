import { http } from "@/lib/api/http";
import type {
  GroupBuyStatus,
  GroupBuySummaryResponse,
  GroupBuySuspensionRequestResponse,
  GroupBuySuspensionStatus,
  PageResponse,
  SellerInfoResponse,
  SellerStatus,
} from "@/lib/api/types";

export function listSellerApplications(params: {
  status: SellerStatus;
  page?: number;
  size?: number;
}): Promise<PageResponse<SellerInfoResponse>> {
  const query = new URLSearchParams({ status: params.status });
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));
  return http.get<PageResponse<SellerInfoResponse>>(`/api/admin/sellers?${query.toString()}`, { auth: true });
}

export function approveSeller(sellerId: number): Promise<void> {
  return http.post<void>(`/api/admin/sellers/${sellerId}/approve`, undefined, { auth: true });
}

export function rejectSeller(sellerId: number): Promise<void> {
  return http.post<void>(`/api/admin/sellers/${sellerId}/reject`, undefined, { auth: true });
}

export function listAdminGroupBuys(params?: {
  status?: GroupBuyStatus;
  page?: number;
  size?: number;
}): Promise<PageResponse<GroupBuySummaryResponse>> {
  const query = new URLSearchParams();
  if (params?.status) query.set("status", params.status);
  if (params?.page !== undefined) query.set("page", String(params.page));
  if (params?.size !== undefined) query.set("size", String(params.size));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return http.get<PageResponse<GroupBuySummaryResponse>>(`/api/admin/groupBuys${suffix}`, { auth: true });
}

export function listSuspensionRequests(params: {
  status: GroupBuySuspensionStatus;
  page?: number;
  size?: number;
}): Promise<PageResponse<GroupBuySuspensionRequestResponse>> {
  const query = new URLSearchParams({ status: params.status });
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));
  return http.get<PageResponse<GroupBuySuspensionRequestResponse>>(
    `/api/admin/groupBuys/suspension-requests?${query.toString()}`,
    { auth: true },
  );
}

export function approveSuspensionRequest(id: number): Promise<void> {
  return http.post<void>(`/api/admin/groupBuys/suspension-requests/${id}/approve`, undefined, { auth: true });
}

export function rejectSuspensionRequest(id: number): Promise<void> {
  return http.post<void>(`/api/admin/groupBuys/suspension-requests/${id}/reject`, undefined, { auth: true });
}
