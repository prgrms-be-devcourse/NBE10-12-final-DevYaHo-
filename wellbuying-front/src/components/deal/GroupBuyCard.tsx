import Link from "next/link";
import { GroupBuyArtwork } from "@/components/deal/GroupBuyArtwork";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { Tag } from "@/components/ui/Tag";
import { won } from "@/lib/format";
import type { GroupBuyCardView } from "@/lib/groupBuy/useGroupBuyList";

export function GroupBuyCard({ item }: { item: GroupBuyCardView }) {
  const progress = item.maxQuantity > 0 ? item.currentQuantity / item.maxQuantity : 0;
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
      <div className="mt-2 flex items-center justify-between">
        <span className="text-sm font-bold">{won(item.currentUnitPrice)}</span>
        <span className="text-xs font-bold text-wb-green">{Math.round(progress * 100)}%</span>
      </div>
      <div className="mt-1.5">
        <ProgressBar value={progress} />
      </div>
    </Link>
  );
}
