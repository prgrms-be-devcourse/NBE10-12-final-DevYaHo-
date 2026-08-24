import type { GroupBuyPriceTier } from "@/lib/api/types";

// 백엔드는 참여 시점에 가격을 계산/저장하지 않고 공동구매가 성사되는 순간에만 최종가를 확정한다.
// 성사 전 "지금 참여하면 얼마"는 프론트가 이 함수로 직접 계산해서 보여준다.
export function resolveCurrentUnitPrice(priceTiers: GroupBuyPriceTier[], currentQuantity: number): number | null {
  const sorted = [...priceTiers].sort((a, b) => a.thresholdQuantity - b.thresholdQuantity);
  const reached = sorted.filter((tier) => tier.thresholdQuantity <= currentQuantity);
  const tier = reached.at(-1) ?? sorted[0];
  return tier ? tier.unitPrice : null;
}
