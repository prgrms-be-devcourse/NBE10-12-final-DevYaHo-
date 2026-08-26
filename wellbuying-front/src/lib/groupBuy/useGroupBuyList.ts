import { useEffect, useState } from "react";
import { listGroupBuys } from "@/lib/api/groupBuy";
import { ApiError } from "@/lib/api/http";
import type { GroupBuyStatus, GroupBuySummaryResponse } from "@/lib/api/types";
import { resolveCatalogEntry } from "@/lib/groupBuy/seedCatalog";
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

// 가격/가격 구간은 목록 응답에 없어서 항목마다 /price를 따로 불러야 했다(N+1) - 목록 카드에는 가격을
// 표시하지 않고, 실제 가격은 상세 페이지 진입 시 그 화면에서만 조회한다.
function toCardView(summary: GroupBuySummaryResponse): GroupBuyCardView {
  const catalog = resolveCatalogEntry(summary.productName);
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
        const views = page.content.map(toCardView);
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
