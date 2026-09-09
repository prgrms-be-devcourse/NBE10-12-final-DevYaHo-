"use client";

import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Search } from "lucide-react";
import { DealsSubNav } from "@/components/consumer/DealsSubNav";
import { GroupBuyCard } from "@/components/deal/GroupBuyCard";
import { Button } from "@/components/ui/Button";
import { CATALOG_CATEGORIES } from "@/lib/groupBuy/seedCatalog";
import { useGroupBuyList } from "@/lib/groupBuy/useGroupBuyList";

type Sort = "popular" | "new" | "closing";

const SORT_LABEL: Record<Sort, string> = {
  popular: "인기순",
  new: "신상품순",
  closing: "마감 임박순",
};

// 프론트 sort 값 -> 서버 Pageable sort 파라미터. 서버가 정렬해서 내려주므로 클라이언트에서 재정렬하지 않는다
const SORT_PARAM: Record<Sort, string> = {
  popular: "viewCount,desc",
  new: "createdAt,desc",
  closing: "endAt,asc",
};

const PAGE_SIZE = 12;

function isSort(value: string | null): value is Sort {
  return !!value && value in SORT_LABEL;
}

export default function ExplorePage() {
  const searchParams = useSearchParams();
  const scheduledView = searchParams.get("status") === "scheduled";

  const [query, setQuery] = useState(() => searchParams.get("q") ?? "");
  const [category, setCategory] = useState("전체");
  const [sort, setSort] = useState<Sort>(() => {
    const param = searchParams.get("sort");
    return isSort(param) ? param : "popular";
  });

  const sortParam = SORT_PARAM[sort];
  const {
    items: ongoing,
    loading: ongoingLoading,
    page: ongoingPage,
    totalPages: ongoingTotalPages,
    setPage: setOngoingPage,
  } = useGroupBuyList("ONGOING", { sort: sortParam, size: PAGE_SIZE });
  const {
    items: scheduled,
    loading: scheduledLoading,
    page: scheduledPage,
    totalPages: scheduledTotalPages,
    setPage: setScheduledPage,
  } = useGroupBuyList("READY", { sort: sortParam, size: PAGE_SIZE });

  const loading = scheduledView ? scheduledLoading : ongoingLoading;
  const page = scheduledView ? scheduledPage : ongoingPage;
  const totalPages = scheduledView ? scheduledTotalPages : ongoingTotalPages;
  const setPage = scheduledView ? setScheduledPage : setOngoingPage;

  useEffect(() => {
    // q가 사라지면(예: 다른 탭 클릭으로 ?q= 없는 URL로 이동) 검색창도 같이 비워야 한다 -
    // 이전 값을 그대로 남겨두면 주소창과 검색창이 서로 다른 값을 보여주게 된다
    setQuery(searchParams.get("q") ?? "");
    const param = searchParams.get("sort");
    if (isSort(param)) setSort(param);
  }, [searchParams]);

  // 카테고리/검색어를 바꾸면 이전 페이지 번호가 새 필터 기준으로는 의미가 없으므로 1페이지로 되돌린다
  useEffect(() => {
    setOngoingPage(0);
    setScheduledPage(0);
  }, [category, query, setOngoingPage, setScheduledPage]);

  const baseDeals = scheduledView ? scheduled : ongoing;

  // 서버가 이미 sort 기준으로 정렬해서 내려주므로 카테고리/검색어로 걸러내기만 한다(재정렬하지 않음).
  // 검색어/카테고리는 서버에 해당 파라미터가 없어 현재 페이지에 이미 불러온 항목 안에서만 매칭된다
  const filtered = useMemo(() => {
    return baseDeals.filter((item) => {
      const matchesCategory = category === "전체" || item.category === category;
      const matchesQuery =
        query.trim().length === 0 ||
        item.title.toLowerCase().includes(query.toLowerCase()) ||
        item.producerName.toLowerCase().includes(query.toLowerCase());
      return matchesCategory && matchesQuery;
    });
  }, [baseDeals, category, query]);

  function resetFilters() {
    setQuery("");
    setCategory("전체");
    setSort("popular");
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-6 py-9">
      <div className="space-y-4">
        <div className="flex items-center justify-between gap-3">
          <DealsSubNav categories={CATALOG_CATEGORIES} categoryValue={category} onCategoryChange={setCategory} />
          <select
            value={sort}
            onChange={(e) => setSort(e.target.value as Sort)}
            className="h-10 shrink-0 rounded-xl border border-wb-line bg-wb-surface px-3 text-sm font-semibold"
          >
            {Object.entries(SORT_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div>
        <h1 className="text-3xl font-bold">{scheduledView ? "진행 예정 공동구매" : "공동구매 둘러보기"}</h1>
        <p className="mt-1 text-sm text-wb-secondary">
          {scheduledView
            ? "곧 시작하는 공동구매를 미리 만나보세요."
            : "카테고리와 가격을 비교해 나에게 맞는 상품을 찾아보세요."}
        </p>
      </div>

      <div className="flex items-center justify-between">
        <p className="text-base font-bold">검색 결과 {filtered.length}개</p>
      </div>

      {loading ? (
        <p className="py-20 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : filtered.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-20 text-center">
          <Search className="h-9 w-9 text-wb-green" strokeWidth={1.5} />
          <p className="text-lg font-bold">조건에 맞는 공동구매가 없어요</p>
          <p className="text-sm text-wb-secondary">검색어나 카테고리를 바꿔보세요.</p>
          <Button variant="secondary" onClick={resetFilters}>
            필터 초기화
          </Button>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {filtered.map((item) => (
              <GroupBuyCard key={item.id} item={item} />
            ))}
          </div>
          {totalPages > 1 && (
            <div className="flex justify-center gap-2">
              <Button
                variant="secondary"
                className="px-3 py-1.5 text-xs"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                이전
              </Button>
              <span className="flex items-center px-2 text-xs text-wb-secondary">
                {page + 1} / {totalPages}
              </span>
              <Button
                variant="secondary"
                className="px-3 py-1.5 text-xs"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                다음
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
