export type ColorToken = "herb" | "citrus" | "ocean";

export const TINT_GRADIENTS: Record<ColorToken, string> = {
  herb: "linear-gradient(135deg, #baD49b, #e8e8b8)",
  citrus: "linear-gradient(135deg, #fac05e, #fee3a1)",
  ocean: "linear-gradient(135deg, #80b8bb, #c4e0dc)",
};

export type PriceTier = {
  minimumPeople: number;
  price: number;
  cost: number;
};

export function tierMargin(tier: PriceTier): number {
  return Math.round(((tier.price - tier.cost) / tier.price) * 100);
}

export function tierProfit(tier: PriceTier): number {
  return tier.price - tier.cost;
}

export type Deal = {
  id: string;
  title: string;
  producer: string;
  category: string;
  summary: string;
  detail: string;
  icon: string;
  tint: ColorToken;
  daysLeft: number;
  targetPeople: number;
  tiers: PriceTier[];
};

export function activeTier(deal: Deal, people: number): PriceTier {
  const sorted = [...deal.tiers].sort((a, b) => a.minimumPeople - b.minimumPeople);
  let result = sorted[0];
  for (const tier of sorted) {
    if (people >= tier.minimumPeople) result = tier;
  }
  return result;
}

export function nextTier(deal: Deal, people: number): PriceTier | null {
  const sorted = [...deal.tiers].sort((a, b) => a.minimumPeople - b.minimumPeople);
  return sorted.find((tier) => people < tier.minimumPeople) ?? null;
}

export type DealStatus =
  | "draft"
  | "scheduled"
  | "recruiting"
  | "paused"
  | "completed"
  | "failed"
  | "cancelled";

export const DEAL_STATUS_LABEL: Record<DealStatus, string> = {
  draft: "작성 중",
  scheduled: "오픈 예정",
  recruiting: "모집 중",
  paused: "일시 중지",
  completed: "모집 완료",
  failed: "목표 미달",
  cancelled: "취소",
};

export type ParticipationState = "recruiting" | "paymentRequired" | "paid" | "cancelled";

export const PARTICIPATION_STATE_LABEL: Record<ParticipationState, string> = {
  recruiting: "모집 중",
  paymentRequired: "결제 필요",
  paid: "결제 완료",
  cancelled: "참여 취소",
};

export type Participation = {
  id: string;
  dealId: string;
  quantity: number;
  reservedPrice: number;
  joinedAt: string;
  state: ParticipationState;
  finalPrice: number | null;
};

export type PaymentMethod = "card" | "kakaoPay" | "bank";

export const PAYMENT_METHOD_LABEL: Record<PaymentMethod, string> = {
  card: "신용·체크카드",
  kakaoPay: "카카오페이",
  bank: "계좌이체",
};

export type PaymentState = "approved" | "cancelled" | "refunded";

export const PAYMENT_STATE_LABEL: Record<PaymentState, string> = {
  approved: "결제 완료",
  cancelled: "결제 취소",
  refunded: "환불 완료",
};

export type DeliveryState = "preparing" | "shipping" | "delivered" | "confirmed";

export const DELIVERY_STATES: DeliveryState[] = ["preparing", "shipping", "delivered", "confirmed"];

export const DELIVERY_STATE_LABEL: Record<DeliveryState, string> = {
  preparing: "상품 준비 중",
  shipping: "배송 중",
  delivered: "배송 완료",
  confirmed: "구매 확정",
};

export type Order = {
  id: string;
  orderNumber: string;
  participationId: string;
  dealId: string;
  quantity: number;
  unitPrice: number;
  paymentMethod: PaymentMethod;
  paymentState: PaymentState;
  deliveryState: DeliveryState;
  orderedAt: string;
};

export type ReviewStatus = "pending" | "approved" | "rejected";

export const REVIEW_STATUS_LABEL: Record<ReviewStatus, string> = {
  pending: "검토 대기",
  approved: "승인",
  rejected: "반려",
};

export type ProductSubmission = {
  id: string;
  title: string;
  producer: string;
  category: string;
  submittedAt: string;
  proposedPrice: number;
};

export type SettlementStatus = "ready" | "completed" | "held";

export const SETTLEMENT_STATUS_LABEL: Record<SettlementStatus, string> = {
  ready: "정산 대기",
  completed: "정산 완료",
  held: "보류",
};

export type SettlementRecord = {
  id: string;
  producer: string;
  groupBuyTitle: string;
  sales: number;
  platformFee: number;
  payout: number;
};

export type AdminMember = {
  id: string;
  nickname: string;
  email: string;
  joinedAt: string;
  participationCount: number;
  status: string;
};

export function won(value: number): string {
  return `${value.toLocaleString("ko-KR")}원`;
}
