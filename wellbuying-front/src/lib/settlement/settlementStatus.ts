import type { SettlementStatus } from "@/lib/api/types";

// 정산 상태 한글 라벨. PAID(실제 지급 완료)는 지급 실행 연동 전이라 아직 나타나지 않지만
// enum에 이미 값이 있어 미리 매핑해 둔다 (orderStatus.ts와 같은 방식)
export const SETTLEMENT_STATUS_LABEL: Record<SettlementStatus, string> = {
  CONFIRMED: "정산 확정",
  PAID: "지급 완료",
};

type Tone = "green" | "orange" | "red" | "blue" | "neutral";

export const SETTLEMENT_STATUS_TONE: Record<SettlementStatus, Tone> = {
  CONFIRMED: "orange",
  PAID: "green",
};
