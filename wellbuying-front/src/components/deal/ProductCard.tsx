import Link from "next/link";
import { useState } from "react";
import { GroupBuyArtwork } from "@/components/deal/GroupBuyArtwork";
import type { ProductSummaryResponse } from "@/lib/api/types";
import { won } from "@/lib/format";
import { resolveCatalogEntry } from "@/lib/groupBuy/seedCatalog";

// "전체 상품" 목록 전용 카드 - 썸네일/이름/가격만 표시한다(공동구매 정보 없음).
export function ProductCard({ item }: { item: ProductSummaryResponse }) {
  const [thumbnailFailed, setThumbnailFailed] = useState(false);
  const catalog = resolveCatalogEntry(item.productName);

  return (
    <Link href={`/products/${item.id}`} className="group block">
      {item.thumbnailUrl && !thumbnailFailed ? (
        // eslint-disable-next-line @next/next/no-img-element -- 판매자가 등록한 외부 썸네일 URL이라 next/image 최적화 대상이 아님
        <img
          src={item.thumbnailUrl}
          alt={item.productName}
          className="h-36 w-full rounded-xl object-cover transition-transform group-hover:scale-[1.03]"
          onError={() => setThumbnailFailed(true)}
        />
      ) : (
        <GroupBuyArtwork entry={catalog} className="h-36 w-full transition-transform group-hover:scale-[1.03]" />
      )}
      <div className="mt-3 space-y-1">
        <p className="line-clamp-2 min-h-12 text-base">{item.productName}</p>
        <p className="text-sm font-bold">{won(item.startPrice)}</p>
      </div>
    </Link>
  );
}
