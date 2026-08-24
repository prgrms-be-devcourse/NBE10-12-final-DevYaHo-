"use client";

import Link from "next/link";
import { Heart } from "lucide-react";
import { DealArtwork } from "@/components/deal/DealArtwork";
import { Tag } from "@/components/ui/Tag";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { activeTier, won, type Deal } from "@/lib/mock/types";

export function DealCard({ deal }: { deal: Deal }) {
  const { participants, favoriteIds, toggleFavorite } = useDemoStore();
  const people = participants(deal.id);
  const isFavorite = favoriteIds.has(deal.id);
  const price = activeTier(deal, people).price;

  return (
    <div className="relative">
      <Link
        href={`/deals/${deal.id}`}
        className="block rounded-xl border border-wb-line bg-wb-surface p-3.5 transition-shadow hover:shadow-md"
      >
        <div className="relative">
          <DealArtwork deal={deal} className="h-36 w-full" />
          <div className="absolute left-2.5 top-2.5">
            <Tag highlighted>D-{deal.daysLeft}</Tag>
          </div>
        </div>
        <div className="mt-3 space-y-1">
          <p className="line-clamp-2 text-base font-bold">{deal.title}</p>
          <p className="text-xs text-wb-secondary">{deal.producer}</p>
        </div>
        <div className="mt-2 flex items-baseline justify-between">
          <span className="text-lg font-bold">{won(price)}</span>
          <span className="text-xs font-bold text-wb-green">{people.toLocaleString("ko-KR")}명</span>
        </div>
      </Link>
      <button
        onClick={(e) => {
          e.preventDefault();
          toggleFavorite(deal.id);
        }}
        aria-label={isFavorite ? "찜 해제" : "찜하기"}
        className="absolute right-5 top-5 flex h-8 w-8 items-center justify-center rounded-full border border-wb-line bg-wb-surface/90"
      >
        <Heart className={`h-3.5 w-3.5 ${isFavorite ? "fill-wb-orange text-wb-orange" : "text-wb-ink"}`} />
      </button>
    </div>
  );
}
