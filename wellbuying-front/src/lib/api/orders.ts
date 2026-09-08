import { http } from "@/lib/api/http";
import type { OrderDetailResponse, OrderSummaryResponse, PageResponse } from "@/lib/api/types";

// 내 결제/주문 내역 목록 (최신순). 본인 것만 반환된다
export function listMyOrders(params?: {
  page?: number;
  size?: number;
}): Promise<PageResponse<OrderSummaryResponse>> {
  const query = new URLSearchParams();
  if (params?.page !== undefined) query.set("page", String(params.page));
  if (params?.size !== undefined) query.set("size", String(params.size));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return http.get<PageResponse<OrderSummaryResponse>>(`/api/orders/me${suffix}`, { auth: true });
}

// 주문 1건의 결제 정보 상세. 남의 주문이면 404
export function getMyOrder(orderId: string): Promise<OrderDetailResponse> {
  return http.get<OrderDetailResponse>(`/api/orders/me/${encodeURIComponent(orderId)}`, { auth: true });
}
