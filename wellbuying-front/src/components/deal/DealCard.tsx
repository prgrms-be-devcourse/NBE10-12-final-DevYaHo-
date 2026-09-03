"use client";

import Link from "next/link";
import { Heart } from "lucide-react";
import { DealArtwork } from "@/components/deal/DealArtwork";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { Tag } from "@/components/ui/Tag";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { activeTier, activeTierIndex, nextTier, won, type Deal } from "@/lib/mock/types";

export function DealCard({ deal, showProgress = false }: { deal: Deal; showProgress?: boolean }) {
  const { participants, favoriteIds, toggleFavorite } = useDemoStore();
  const people = participants(deal.id);
  const isFavorite = favoriteIds.has(deal.id);
  const price = activeTier(deal, people).price;
  const tierIndex = activeTierIndex(deal, people);
  const next = nextTier(deal, people);

  return (
    <div className="group relative">
      <Link href={`/deals/${deal.id}`} className="block">
        <div className="relative">
          <DealArtwork deal={deal} className="h-36 w-full transition-transform group-hover:scale-[1.03]" />
          <div className="absolute left-2.5 top-2.5">
            <Tag highlighted>D-{deal.daysLeft}</Tag>
          </div>
        </div>
        <div className="mt-3 space-y-1">
          <p className="line-clamp-2 min-h-12 text-base">{deal.title}</p>
          <p className="text-xs text-wb-secondary">{deal.producer}</p>
        </div>
        <div className="mt-2 flex items-baseline justify-between">
          <span className="text-lg font-bold">{won(price)}</span>
          <span className="text-xs font-bold text-wb-green">{people.toLocaleString("ko-KR")}명</span>
        </div>
        {showProgress && (
          <div className="mt-2 space-y-1.5">
            <div className="flex items-center justify-between text-[11px] font-semibold text-wb-secondary">
              <span>
                {tierIndex + 1}/{deal.tiers.length} 구간
              </span>
              {next ? (
                <span>다음 구간까지 {(next.minimumPeople - people).toLocaleString("ko-KR")}명</span>
              ) : (
                <span className="text-wb-green">최저가 달성</span>
              )}
            </div>
            <ProgressBar value={people / deal.targetPeople} />
          </div>
        )}
      </Link>
      <button
        onClick={(e) => {
          e.preventDefault();
          toggleFavorite(deal.id);
        }}
        aria-label={isFavorite ? "찜 해제" : "찜하기"}
        className="absolute right-2.5 top-2.5 flex h-8 w-8 items-center justify-center rounded-full border border-wb-line bg-wb-surface/90"
      >
        <Heart className={`h-3.5 w-3.5 ${isFavorite ? "fill-wb-orange text-wb-orange" : "text-wb-ink"}`} />
      </button>
    </div>
  );
}
