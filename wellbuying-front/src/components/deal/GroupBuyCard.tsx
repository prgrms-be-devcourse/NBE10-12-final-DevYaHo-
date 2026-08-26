import Link from "next/link";
import { GroupBuyArtwork } from "@/components/deal/GroupBuyArtwork";
import { Tag } from "@/components/ui/Tag";
import type { GroupBuyCardView } from "@/lib/groupBuy/useGroupBuyList";

export function GroupBuyCard({ item }: { item: GroupBuyCardView }) {
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
      <div className="mt-2 flex items-center justify-end">
        <span className="text-xs font-bold text-wb-green">{item.currentQuantity.toLocaleString("ko-KR")}개 참여</span>
      </div>
    </Link>
  );
}
