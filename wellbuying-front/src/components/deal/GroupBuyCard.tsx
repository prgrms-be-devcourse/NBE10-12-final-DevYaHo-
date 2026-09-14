import Link from "next/link";
import { useState } from "react";
import { GroupBuyArtwork } from "@/components/deal/GroupBuyArtwork";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { Tag } from "@/components/ui/Tag";
import { won } from "@/lib/format";
import type { GroupBuyCardView } from "@/lib/groupBuy/useGroupBuyList";

export function GroupBuyCard({ item }: { item: GroupBuyCardView }) {
  const [thumbnailFailed, setThumbnailFailed] = useState(false);
  const progress = item.maxQuantity > 0 ? item.currentQuantity / item.maxQuantity : 0;
  const isReady = item.status === "READY";
  return (
    <Link href={`/deals/${item.id}`} className="group block">
      <div className="relative">
        {item.thumbnailUrl && !thumbnailFailed ? (
          // eslint-disable-next-line @next/next/no-img-element -- 판매자가 등록한 외부 썸네일 URL이라 next/image 최적화 대상이 아님
          <img
            src={item.thumbnailUrl}
            alt={item.title}
            className="h-64 w-full rounded-2xl object-cover transition-transform group-hover:scale-[1.03]"
            onError={() => setThumbnailFailed(true)}
          />
        ) : (
          <GroupBuyArtwork entry={item} className="h-64 w-full transition-transform group-hover:scale-[1.03]" />
        )}
        <div className="absolute left-2.5 top-2.5">
          <Tag highlighted>{isReady ? `${item.daysUntilStart}일 후 오픈` : `마감 D-${item.daysLeft}`}</Tag>
        </div>
      </div>
      <div className="mt-3 space-y-1">
        <p className="line-clamp-2 min-h-12 text-base">{item.title}</p>
        <p className="truncate text-xs text-wb-secondary">{item.summary}</p>
        <p className="truncate text-[11px] text-wb-secondary/70">{item.producerName}</p>
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
