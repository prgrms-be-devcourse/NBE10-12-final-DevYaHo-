import { http } from "@/lib/api/http";
import type { PageResponse, SellerInfoResponse, SellerStatus } from "@/lib/api/types";

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
