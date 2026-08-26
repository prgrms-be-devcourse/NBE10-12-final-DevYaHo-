import Link from "next/link";
import { GroupBuyArtwork } from "@/components/deal/GroupBuyArtwork";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { Tag } from "@/components/ui/Tag";
import type { GroupBuyCardView } from "@/lib/groupBuy/useGroupBuyList";
import { won } from "@/lib/format";
import { resolveNextTier } from "@/lib/groupBuyPricing";

export function GroupBuyCard({ item, showProgress = false }: { item: GroupBuyCardView; showProgress?: boolean }) {
  const sortedTiers = [...item.priceTiers].sort((a, b) => a.thresholdQuantity - b.thresholdQuantity);
  const tierIndex = sortedTiers.reduce(
    (active, tier, i) => (tier.thresholdQuantity <= item.currentQuantity ? i : active),
    0,
  );
  const next = resolveNextTier(item.priceTiers, item.currentQuantity);

  return (
    <Link href={`/deals/${item.id}`} className="group block">
      <div className="relative">
        <GroupBuyArtwork entry={item} className="h-36 w-full transition-transform group-hover:scale-[1.03]" />
        <div className="absolute left-2.5 top-2.5">
          <Tag highlighted>D-{item.daysLeft}</Tag>
        </div>
      </div>
      <div className="mt-3 space-y-1">
        <p className="line-clamp-2 min-h-12 text-base">{item.title}</p>
        <p className="text-xs text-wb-secondary">{item.producerName}</p>
      </div>
      <div className="mt-2 flex items-baseline justify-between">
        <span className="text-lg font-bold">{item.price !== null ? won(item.price) : "-"}</span>
        <span className="text-xs font-bold text-wb-green">{item.currentQuantity.toLocaleString("ko-KR")}개</span>
      </div>
      {showProgress && (
        <div className="mt-2 space-y-1.5">
          <div className="flex items-center justify-between text-[11px] font-semibold text-wb-secondary">
            <span>
              {tierIndex + 1}/{sortedTiers.length} 구간
            </span>
            {next ? (
              <span>다음 구간까지 {(next.thresholdQuantity - item.currentQuantity).toLocaleString("ko-KR")}개</span>
            ) : (
              <span className="text-wb-green">최저가 달성</span>
            )}
          </div>
          <ProgressBar value={item.maxQuantity === 0 ? 0 : item.currentQuantity / item.maxQuantity} />
        </div>
      )}
    </Link>
  );
}
