"use client";

import { Heart } from "lucide-react";
import { DealCard } from "@/components/deal/DealCard";
import { EmptyState } from "@/components/ui/EmptyState";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";

export default function FavoritesPage() {
  const { deals, favoriteIds } = useDemoStore();
  const favorited = deals.filter((deal) => favoriteIds.has(deal.id));

  if (favorited.length === 0) {
    return (
      <EmptyState
        icon={Heart}
        title="아직 찜한 공동구매가 없어요"
        message="관심 있는 상품의 하트를 눌러 모아보세요."
      />
    );
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-6 py-9">
      <h1 className="text-3xl font-bold">찜한 공동구매</h1>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {favorited.map((deal) => (
          <DealCard key={deal.id} deal={deal} />
        ))}
      </div>
    </div>
  );
}
