import { useCallback, useEffect, useState } from "react";
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
  viewCount: number;
  createdAt: string;
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
    viewCount: summary.viewCount,
    createdAt: summary.createdAt,
    category: summary.productCategory,
    ...catalog,
  };
}

// sort: Spring Pageable 형식("속성,방향", 예: "viewCount,desc") - 서버가 이 기준으로 정렬해서 내려주므로
// size로 잘라도(예: 상위 4개) 순서가 항상 정확하다. 미지정 시 서버 기본 정렬(createdAt desc)을 따른다
export function useGroupBuyList(status: GroupBuyStatus, options?: { sort?: string; size?: number }) {
  const [items, setItems] = useState<GroupBuyCardView[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const sort = options?.sort;
  const size = options?.size ?? 50;

  useEffect(() => {
    let ignore = false;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        const page = await listGroupBuys({ status, size, sort, page: 0 });
        if (!ignore) {
          setItems(page.content.map(toCardView));
          setPageNumber(0);
          setHasMore(page.page.number + 1 < page.page.totalPages);
        }
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
  }, [status, sort, size]);

  // 다음 페이지를 서버에서 이어받아 기존 목록 뒤에 붙인다("더보기" 버튼용) - 이미 앞에서
  // 서버 정렬로 받아온 결과이므로 클라이언트에서 순서를 다시 계산할 필요가 없다
  const loadMore = useCallback(async () => {
    if (loadingMore || !hasMore) return;
    const nextPage = pageNumber + 1;
    setLoadingMore(true);
    try {
      const page = await listGroupBuys({ status, size, sort, page: nextPage });
      setItems((prev) => [...prev, ...page.content.map(toCardView)]);
      setPageNumber(nextPage);
      setHasMore(page.page.number + 1 < page.page.totalPages);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "목록을 불러오지 못했어요.");
    } finally {
      setLoadingMore(false);
    }
  }, [status, sort, size, pageNumber, hasMore, loadingMore]);

  return { items, loading, loadingMore, error, hasMore, loadMore };
}
