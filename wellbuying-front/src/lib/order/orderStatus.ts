import type { OrderStatus } from "@/lib/api/types";

// 주문 상태 한글 라벨. 배송 상태(PREPARING~DELIVERED)는 shipping 도메인이 붙기 전까지 나타나지 않지만,
// enum에 이미 값이 있어 미리 매핑해 둔다
export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING: "결제 대기",
  PAID: "결제 완료",
  PAYMENT_FAILED: "결제 실패",
  PREPARING: "상품 준비 중",
  SHIPPING: "배송 중",
  DELIVERED: "배송 완료",
  CONFIRMED: "구매 확정",
  CANCELED: "취소",
};

type Tone = "green" | "orange" | "red" | "blue" | "neutral";

export const ORDER_STATUS_TONE: Record<OrderStatus, Tone> = {
  PENDING: "orange",
  PAID: "green",
  PAYMENT_FAILED: "red",
  PREPARING: "blue",
  SHIPPING: "blue",
  DELIVERED: "green",
  CONFIRMED: "green",
  CANCELED: "neutral",
};
