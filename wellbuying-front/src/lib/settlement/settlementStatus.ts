import type { SettlementListStatus, SettlementStatus } from "@/lib/api/types";

// 관리자 정산 내역(GET /api/admin/settlements)의 상태 라벨. PAID(실제 지급 완료)는 지급 실행
// 연동 전이라 아직 나타나지 않지만 enum에 이미 값이 있어 미리 매핑해 둔다 (orderStatus.ts와 같은 방식)
export const SETTLEMENT_STATUS_LABEL: Record<SettlementStatus, string> = {
  CONFIRMED: "정산 확정",
  PAID: "지급 완료",
};

type Tone = "green" | "orange" | "red" | "blue" | "neutral";

export const SETTLEMENT_STATUS_TONE: Record<SettlementStatus, Tone> = {
  CONFIRMED: "orange",
  PAID: "green",
};

// 판매자 정산 목록(GET /api/settlements/me)의 필터/표시 상태 라벨 - 관리자용과는 별개
export const SETTLEMENT_LIST_STATUS_LABEL: Record<SettlementListStatus, string> = {
  PENDING: "정산 대기중",
  COMPLETED: "정산 완료",
};

export const SETTLEMENT_LIST_STATUS_TONE: Record<SettlementListStatus, Tone> = {
  PENDING: "orange",
  COMPLETED: "green",
};
