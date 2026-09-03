"use client";

import { Heart } from "lucide-react";
import { AccountShell } from "@/components/account/AccountShell";
import { DealCard } from "@/components/deal/DealCard";
import { EmptyState } from "@/components/ui/EmptyState";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";

function FavoritesContent() {
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
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {favorited.map((deal) => (
        <DealCard key={deal.id} deal={deal} />
      ))}
    </div>
  );
}

export default function FavoritesPage() {
  return (
    <AccountShell>
      <FavoritesContent />
    </AccountShell>
  );
}
