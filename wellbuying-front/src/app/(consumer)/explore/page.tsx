"use client";

import { useMemo, useState } from "react";
import { Search } from "lucide-react";
import { DealCard } from "@/components/deal/DealCard";
import { Button } from "@/components/ui/Button";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { activeTier, type Deal } from "@/lib/mock/types";

const CATEGORIES = ["전체", "식품", "생활", "패션"];

type Sort = "popular" | "priceLow" | "priceHigh" | "closing";

const SORT_LABEL: Record<Sort, string> = {
  popular: "인기순",
  priceLow: "낮은 가격순",
  priceHigh: "높은 가격순",
  closing: "마감 임박순",
};

export default function ExplorePage() {
  const { visibleDeals, participants } = useDemoStore();
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("전체");
  const [sort, setSort] = useState<Sort>("popular");
  const [visibleCount, setVisibleCount] = useState(6);

  const filtered = useMemo(() => {
    const price = (deal: Deal) => activeTier(deal, participants(deal.id)).price;
    const result = visibleDeals().filter((deal) => {
      const matchesCategory = category === "전체" || deal.category === category;
      const matchesQuery =
        query.trim().length === 0 ||
        deal.title.toLowerCase().includes(query.toLowerCase()) ||
        deal.producer.toLowerCase().includes(query.toLowerCase());
      return matchesCategory && matchesQuery;
    });

    return result.sort((a, b) => {
      switch (sort) {
        case "priceLow":
          return price(a) - price(b);
        case "priceHigh":
          return price(b) - price(a);
        case "closing":
          return a.daysLeft - b.daysLeft;
        default:
          return participants(b.id) - participants(a.id);
      }
    });
  }, [visibleDeals, participants, category, query, sort]);

  const visible = filtered.slice(0, visibleCount);

  function resetFilters() {
    setQuery("");
    setCategory("전체");
    setSort("popular");
    setVisibleCount(6);
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-6 py-9">
      <div>
        <h1 className="text-3xl font-bold">공동구매 둘러보기</h1>
        <p className="mt-1 text-sm text-wb-secondary">카테고리와 가격을 비교해 나에게 맞는 상품을 찾아보세요.</p>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row">
        <div className="flex h-12 flex-1 items-center gap-2.5 rounded-xl border border-wb-line bg-wb-surface px-4">
          <Search className="h-4 w-4 shrink-0 text-wb-secondary" />
          <input
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setVisibleCount(6);
            }}
            placeholder="상품이나 생산자를 검색해보세요"
            className="w-full bg-transparent text-sm outline-none placeholder:text-wb-secondary"
          />
        </div>
        <select
          value={sort}
          onChange={(e) => setSort(e.target.value as Sort)}
          className="h-12 rounded-xl border border-wb-line bg-wb-surface px-3 text-sm font-semibold"
        >
          {Object.entries(SORT_LABEL).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>

      <div className="flex flex-wrap gap-2">
        {CATEGORIES.map((item) => (
          <button
            key={item}
            onClick={() => {
              setCategory(item);
              setVisibleCount(6);
            }}
            className={`rounded-full px-4 py-2 text-xs font-semibold transition-colors ${
              category === item ? "bg-wb-green text-white" : "bg-wb-canvas text-wb-secondary"
            }`}
          >
            {item}
          </button>
        ))}
      </div>

      <div className="flex items-center justify-between">
        <p className="text-base font-bold">검색 결과 {filtered.length}개</p>
      </div>

      {filtered.length === 0 ? (
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
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {visible.map((deal) => (
              <DealCard key={deal.id} deal={deal} />
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
