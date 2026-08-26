"use client";

import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Search } from "lucide-react";
import { DealsSubNav } from "@/components/consumer/DealsSubNav";
import { GroupBuyCard } from "@/components/deal/GroupBuyCard";
import { Button } from "@/components/ui/Button";
import { CATALOG_CATEGORIES } from "@/lib/groupBuy/seedCatalog";
import { useGroupBuyList, type GroupBuyCardView } from "@/lib/groupBuy/useGroupBuyList";

type Sort = "popular" | "new" | "priceLow" | "priceHigh" | "closing";

const SORT_LABEL: Record<Sort, string> = {
  popular: "인기순",
  new: "신상품순",
  priceLow: "낮은 가격순",
  priceHigh: "높은 가격순",
  closing: "마감 임박순",
};

function isSort(value: string | null): value is Sort {
  return !!value && value in SORT_LABEL;
}

export default function ExplorePage() {
  const searchParams = useSearchParams();
  const scheduledView = searchParams.get("status") === "scheduled";
  const { items: ongoing, loading: ongoingLoading } = useGroupBuyList("ONGOING");
  const { items: scheduled, loading: scheduledLoading } = useGroupBuyList("READY");
  const loading = scheduledView ? scheduledLoading : ongoingLoading;

  const [query, setQuery] = useState(() => searchParams.get("q") ?? "");
  const [category, setCategory] = useState("전체");
  const [sort, setSort] = useState<Sort>(() => {
    const param = searchParams.get("sort");
    return isSort(param) ? param : "popular";
  });
  const [visibleCount, setVisibleCount] = useState(6);

  useEffect(() => {
    // q가 사라지면(예: 다른 탭 클릭으로 ?q= 없는 URL로 이동) 검색창도 같이 비워야 한다 -
    // 이전 값을 그대로 남겨두면 주소창과 검색창이 서로 다른 값을 보여주게 된다
    setQuery(searchParams.get("q") ?? "");
    const param = searchParams.get("sort");
    if (isSort(param)) setSort(param);
    setVisibleCount(6);
  }, [searchParams]);

  const baseDeals = scheduledView ? scheduled : ongoing;

  const filtered = useMemo(() => {
    const result = baseDeals.filter((item) => {
      const matchesCategory = category === "전체" || item.category === category;
      const matchesQuery =
        query.trim().length === 0 ||
        item.title.toLowerCase().includes(query.toLowerCase()) ||
        item.producerName.toLowerCase().includes(query.toLowerCase());
      return matchesCategory && matchesQuery;
    });

    return [...result].sort((a: GroupBuyCardView, b: GroupBuyCardView) => {
      switch (sort) {
        case "priceLow":
          return (a.price ?? 0) - (b.price ?? 0);
        case "priceHigh":
          return (b.price ?? 0) - (a.price ?? 0);
        case "closing":
          return a.daysLeft - b.daysLeft;
        case "new":
          return baseDeals.indexOf(b) - baseDeals.indexOf(a);
        default:
          return b.currentQuantity - a.currentQuantity;
      }
    });
  }, [baseDeals, category, query, sort]);

  const visible = filtered.slice(0, visibleCount);

  function resetFilters() {
    setQuery("");
    setCategory("전체");
    setSort("popular");
    setVisibleCount(6);
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-6 py-9">
      <div className="space-y-4">
        <div className="flex items-center justify-between gap-3">
          <DealsSubNav
            categories={CATALOG_CATEGORIES}
            categoryValue={category}
            onCategoryChange={(value) => {
              setCategory(value);
              setVisibleCount(6);
            }}
          />
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
            {visible.map((item) => (
              <GroupBuyCard key={item.id} item={item} showProgress />
            ))}
          </div>
          {filtered.length > visible.length && (
            <Button variant="secondary" className="w-full" onClick={() => setVisibleCount((v) => v + 6)}>
              상품 더 불러오기
            </Button>
          )}
        </>
      )}
    </div>
  );
}
