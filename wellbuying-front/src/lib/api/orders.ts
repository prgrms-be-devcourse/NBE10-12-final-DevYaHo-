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

// 알림 클릭 시 groupBuyId만으로 해당 주문을 찾기 위한 조회. 결제 재시도로 실패한 주문이 이력으로
// 남은 채 새 주문이 추가될 수 있어 참여 1건당 주문이 여러 건일 수 있는데, 백엔드가 그중 가장 최근
// 주문 하나를 골라 돌려준다
export function getMyOrderIdByGroupBuy(groupBuyId: number): Promise<{ orderId: string }> {
  return http.get<{ orderId: string }>(`/api/orders/me/by-group-buy/${groupBuyId}`, { auth: true });
}

// 결제 실패한 주문 재시도 - 실패했던 주문은 이력으로 남고, 새로 만들어진 주문의 상세가 돌아온다
export function retryPayment(orderId: string): Promise<OrderDetailResponse> {
  return http.post<OrderDetailResponse>(`/api/payments/${encodeURIComponent(orderId)}/retry`, undefined, {
    auth: true,
  });
}
