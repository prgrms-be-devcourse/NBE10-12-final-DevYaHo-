import { http } from "@/lib/api/http";
import type {
  AdminActionLogResponse,
  GroupBuyStatus,
  GroupBuySummaryResponse,
  GroupBuySuspensionRequestResponse,
  GroupBuySuspensionStatus,
  MemberStatus,
  MemberSummaryResponse,
  PageResponse,
  ProductAdminResponse,
  ProductDeletedAdminResponse,
  ProductStatus,
  Role,
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

export function approveSeller(sellerId: number, reason: string): Promise<void> {
  return http.post<void>(`/api/admin/sellers/${sellerId}/approve`, { reason }, { auth: true });
}

export function rejectSeller(sellerId: number, reason: string): Promise<void> {
  return http.post<void>(`/api/admin/sellers/${sellerId}/reject`, { reason }, { auth: true });
}

export function suspendSeller(sellerId: number, reason: string): Promise<void> {
  return http.post<void>(`/api/admin/sellers/${sellerId}/suspend`, { reason }, { auth: true });
}

export function reactivateSeller(sellerId: number, reason: string): Promise<void> {
  return http.post<void>(`/api/admin/sellers/${sellerId}/reactivate`, { reason }, { auth: true });
}

export function listAdminProducts(params: {
  status: ProductStatus;
  keyword?: string;
  page?: number;
  size?: number;
}): Promise<PageResponse<ProductAdminResponse>> {
  const query = new URLSearchParams({ status: params.status });
  if (params.keyword) query.set("keyword", params.keyword);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));
  return http.get<PageResponse<ProductAdminResponse>>(`/api/admin/products?${query.toString()}`, { auth: true });
}

export function approveProduct(productId: number, reason: string): Promise<void> {
  return http.post<void>(`/api/admin/products/${productId}/approve`, { reason }, { auth: true });
}

export function rejectProduct(productId: number, reason: string): Promise<void> {
  return http.post<void>(`/api/admin/products/${productId}/reject`, { reason }, { auth: true });
}

export function listDeletedProducts(params?: {
  page?: number;
  size?: number;
}): Promise<PageResponse<ProductDeletedAdminResponse>> {
  const query = new URLSearchParams();
  if (params?.page !== undefined) query.set("page", String(params.page));
  if (params?.size !== undefined) query.set("size", String(params.size));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return http.get<PageResponse<ProductDeletedAdminResponse>>(`/api/admin/products/deleted${suffix}`, {
    auth: true,
  });
}

export function forceDeleteProduct(productId: number, reason: string): Promise<void> {
  return http.post<void>(`/api/admin/products/${productId}/force-delete`, { reason }, { auth: true });
}

export function listMembers(params?: {
  role?: Role;
  status?: MemberStatus;
  page?: number;
  size?: number;
}): Promise<PageResponse<MemberSummaryResponse>> {
  const query = new URLSearchParams();
  if (params?.role) query.set("role", params.role);
  if (params?.status) query.set("status", params.status);
  if (params?.page !== undefined) query.set("page", String(params.page));
  if (params?.size !== undefined) query.set("size", String(params.size));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return http.get<PageResponse<MemberSummaryResponse>>(`/api/admin/members${suffix}`, { auth: true });
}

export function listAdminGroupBuys(params?: {
  status?: GroupBuyStatus;
  keyword?: string;
  page?: number;
  size?: number;
}): Promise<PageResponse<GroupBuySummaryResponse>> {
  const query = new URLSearchParams();
  if (params?.status) query.set("status", params.status);
  if (params?.keyword) query.set("keyword", params.keyword);
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

export function approveSuspensionRequest(id: number, reason: string): Promise<void> {
  return http.post<void>(`/api/admin/groupBuys/suspension-requests/${id}/approve`, { reason }, { auth: true });
}

export function rejectSuspensionRequest(id: number, reason: string): Promise<void> {
  return http.post<void>(`/api/admin/groupBuys/suspension-requests/${id}/reject`, { reason }, { auth: true });
}

// 상품 승인/반려 이력
export function listProductActionLogs(params?: { page?: number; size?: number }): Promise<PageResponse<AdminActionLogResponse>> {
  const query = new URLSearchParams();
  if (params?.page !== undefined) query.set("page", String(params.page));
  if (params?.size !== undefined) query.set("size", String(params.size));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return http.get<PageResponse<AdminActionLogResponse>>(`/api/admin/products/action-logs${suffix}`, { auth: true });
}

// 공동구매 판매정지 요청 승인/반려 이력
export function listGroupBuySuspensionActionLogs(params?: { page?: number; size?: number }): Promise<PageResponse<AdminActionLogResponse>> {
  const query = new URLSearchParams();
  if (params?.page !== undefined) query.set("page", String(params.page));
  if (params?.size !== undefined) query.set("size", String(params.size));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return http.get<PageResponse<AdminActionLogResponse>>(`/api/admin/groupBuys/suspension-requests/action-logs${suffix}`, { auth: true });
}

// 판매자 전환(승인/거절) 이력
export function listSellerConversionActionLogs(params?: { page?: number; size?: number }): Promise<PageResponse<AdminActionLogResponse>> {
  const query = new URLSearchParams();
  if (params?.page !== undefined) query.set("page", String(params.page));
  if (params?.size !== undefined) query.set("size", String(params.size));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return http.get<PageResponse<AdminActionLogResponse>>(`/api/admin/sellers/action-logs/conversion${suffix}`, { auth: true });
}

// 판매자 정지/정지복귀 이력
export function listSellerSuspensionActionLogs(params?: { page?: number; size?: number }): Promise<PageResponse<AdminActionLogResponse>> {
  const query = new URLSearchParams();
  if (params?.page !== undefined) query.set("page", String(params.page));
  if (params?.size !== undefined) query.set("size", String(params.size));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return http.get<PageResponse<AdminActionLogResponse>>(`/api/admin/sellers/action-logs/suspension${suffix}`, { auth: true });
}
