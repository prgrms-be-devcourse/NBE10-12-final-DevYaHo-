import { useEffect, useState } from "react";
import { getGroupBuyPriceTiers, listGroupBuys } from "@/lib/api/groupBuy";
import { ApiError } from "@/lib/api/http";
import type { GroupBuyPriceTier, GroupBuyStatus, GroupBuySummaryResponse } from "@/lib/api/types";
import { resolveCatalogEntry } from "@/lib/groupBuy/seedCatalog";
import { resolveCurrentUnitPrice } from "@/lib/groupBuyPricing";
import type { ColorToken } from "@/lib/mock/types";

export type GroupBuyCardView = {
  id: number;
  productId: number;
  productName: string;
  title: string;
  status: GroupBuyStatus;
  startAt: string;
  endAt: string;
  currentQuantity: number;
  maxQuantity: number;
  price: number | null;
  priceTiers: GroupBuyPriceTier[];
  daysLeft: number;
  producerName: string;
  category: string;
  icon: string;
  tint: ColorToken;
  summary: string;
  detail: string;
};

function toDaysLeft(endAt: string): number {
  const diffMs = new Date(endAt).getTime() - Date.now();
  return Math.max(0, Math.ceil(diffMs / (1000 * 60 * 60 * 24)));
}

// 카드 목록에서 가격은 캐탈로그에 하드코딩하지 않고 항상 실제 /price API 기준으로 보여준다.
// 페이지당 최대 수십 개 규모라 항목별 병렬 호출(N+1)이어도 로컬/소규모 운영에서는 무리 없다.
async function toCardView(summary: GroupBuySummaryResponse): Promise<GroupBuyCardView> {
  const catalog = resolveCatalogEntry(summary.productName);
  let price: number | null = null;
  let priceTiers: GroupBuyPriceTier[] = [];
  try {
    priceTiers = await getGroupBuyPriceTiers(summary.id);
    price = resolveCurrentUnitPrice(priceTiers, summary.currentQuantity);
  } catch {
    price = null;
  }
  return {
    id: summary.id,
    productId: summary.productId,
    productName: summary.productName,
    title: summary.title,
    status: summary.status,
    startAt: summary.startAt,
    endAt: summary.endAt,
    currentQuantity: summary.currentQuantity,
    maxQuantity: summary.maxQuantity,
    price,
    priceTiers,
    daysLeft: toDaysLeft(summary.endAt),
    category: summary.productCategory,
    ...catalog,
  };
}

export function useGroupBuyList(status: GroupBuyStatus) {
  const [items, setItems] = useState<GroupBuyCardView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        const page = await listGroupBuys({ status, size: 50 });
        const views = await Promise.all(page.content.map(toCardView));
        if (!ignore) setItems(views);
      } catch (e) {
        if (!ignore) setError(e instanceof ApiError ? e.message : "목록을 불러오지 못했어요.");
      } finally {
        if (!ignore) setLoading(false);
      }
    }

    load();
    return () => {
      ignore = true;
    };
  }, [status]);

  return { items, loading, error };
}
