import { useEffect, useRef, useState } from "react";
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
  currentUnitPrice: number;
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

export function toDaysLeft(endAt: string): number {
  const diffMs = new Date(endAt).getTime() - Date.now();
  return Math.max(0, Math.ceil(diffMs / (1000 * 60 * 60 * 24)));
}

// currentUnitPrice(현재 누적 참여 수량 기준 단가)는 목록 응답에 이미 포함되어 있어 카드에 그대로 노출한다
// (백엔드가 IN 쿼리 배치 조회로 계산 - 항목마다 /price를 따로 부르는 N+1은 없다)
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
    currentUnitPrice: summary.currentUnitPrice,
    daysLeft: toDaysLeft(summary.endAt),
    viewCount: summary.viewCount,
    createdAt: summary.createdAt,
    category: summary.productCategory,
    ...catalog,
    summary: summary.description || catalog.summary,
  };
}

// sort: Spring Pageable 형식("속성,방향", 예: "viewCount,desc") - 서버가 이 기준으로 정렬해서 내려주므로
// size로 잘라도(예: 상위 4개) 순서가 항상 정확하다. 미지정 시 서버 기본 정렬(createdAt desc)을 따른다
export function useGroupBuyList(
  status: GroupBuyStatus,
  options?: { sort?: string; size?: number; categoryId?: number; enabled?: boolean },
) {
  const [items, setItems] = useState<GroupBuyCardView[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const sort = options?.sort;
  const size = options?.size ?? 50;
  const categoryId = options?.categoryId;
  // 카테고리 이름 -> id 변환이 비동기로 끝나는 동안(예: page.tsx의 productCategories 로딩 중)
  // categoryId가 아직 확정되지 않은 상태 - 기본값 true라 categoryId를 안 쓰는 화면은 그대로 동작한다
  const enabled = options?.enabled ?? true;
  // 스크롤 중 IntersectionObserver가 loadingMore state가 반영되기 전에 짧은 간격으로 여러 번 발화할 수
  // 있어, state보다 즉시 반영되는 ref로 같은 페이지가 중복 요청되는 것(중복 key로 이어짐)을 막는다
  const fetchingRef = useRef(false);

  // sort/size/status/categoryId가 바뀌면 이전 조건으로 쌓아온 목록은 더 이상 의미가 없으므로 1페이지부터 다시 쌓는다
  useEffect(() => {
    setPage(0);
    setItems([]);
  }, [status, sort, size, categoryId]);

  useEffect(() => {
    if (!enabled) return;
    let ignore = false;

    async function load() {
      fetchingRef.current = true;
      if (page === 0) setLoading(true);
      else setLoadingMore(true);
      setError(null);
      try {
        const result = await listGroupBuys({ status, size, sort, page, categoryId });
        if (!ignore) {
          const mapped = result.content.map(toCardView);
          setItems((prev) => {
            if (page === 0) return mapped;
            const seenIds = new Set(prev.map((item) => item.id));
            return [...prev, ...mapped.filter((item) => !seenIds.has(item.id))];
          });
          setTotalPages(result.page.totalPages);
          setTotalElements(result.page.totalElements);
        }
      } catch (e) {
        if (!ignore) setError(e instanceof ApiError ? e.message : "목록을 불러오지 못했어요.");
      } finally {
        fetchingRef.current = false;
        if (!ignore) {
          setLoading(false);
          setLoadingMore(false);
        }
      }
    }

    load();
    return () => {
      ignore = true;
    };
  }, [status, sort, size, page, categoryId, enabled]);

  const hasNext = page + 1 < totalPages;

  function loadMore() {
    if (!hasNext || fetchingRef.current) return;
    setPage((p) => p + 1);
  }

  return { items, loading, loadingMore, error, hasNext, totalElements, loadMore };
}
