"use client";

import { useMemo, useState } from "react";
import { DealCard } from "@/components/deal/DealCard";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";

const CATEGORIES = ["전체", "식품", "생활", "패션"];

export default function RankingPage() {
  const { visibleDeals, participants } = useDemoStore();
  const [category, setCategory] = useState("전체");

  const deals = visibleDeals();
  const ranked = useMemo(() => {
    const filtered = category === "전체" ? deals : deals.filter((deal) => deal.category === category);
    return [...filtered].sort((a, b) => participants(b.id) - participants(a.id));
  }, [deals, participants, category]);

  return (
    <div className="mx-auto max-w-6xl space-y-5 px-6 py-9">
      <div>
        <h1 className="text-3xl font-bold">인기 공동구매</h1>
        <p className="mt-1 text-sm text-wb-secondary">지금 가장 많은 사람이 참여하고 있는 공동구매예요.</p>
      </div>

      <div className="flex flex-wrap gap-2">
        {CATEGORIES.map((item) => (
          <button
            key={item}
            onClick={() => setCategory(item)}
            className={`rounded-full px-4 py-2 text-xs font-semibold transition-colors ${
              category === item ? "bg-wb-green text-white" : "bg-wb-canvas text-wb-secondary hover:bg-wb-tag-surface"
            }`}
          >
            {item}
          </button>
        ))}
      </div>

      <p className="text-base font-bold">{ranked.length}개의 공동구매가 있어요</p>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
        {ranked.map((deal) => (
          <DealCard key={deal.id} deal={deal} showProgress />
        ))}
      </div>
    </div>
  );
}
