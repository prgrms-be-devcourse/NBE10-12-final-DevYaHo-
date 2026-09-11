import Link from "next/link";
import { useState } from "react";
import { GroupBuyArtwork } from "@/components/deal/GroupBuyArtwork";
import { Tag } from "@/components/ui/Tag";
import type { ProductSearchResponse } from "@/lib/api/types";
import { won } from "@/lib/format";
import { resolveCatalogEntry } from "@/lib/groupBuy/seedCatalog";

function toDaysLeft(endAt: string | null): number | null {
  if (!endAt) return null;
  const diffMs = new Date(endAt).getTime() - Date.now();
  return Math.max(0, Math.ceil(diffMs / (1000 * 60 * 60 * 24)));
}

export type ProductSearchCardVariant = "product" | "groupBuy";

// variant="product": 검색 "상품" 섹션 전용 - 공동구매 진행 여부와 무관하게 항상 상품
// 상세(/products/{id})로 링크하고, 썸네일/이름/가격만 보여준다.
// variant="groupBuy": 검색 "진행 중인 공동구매" 섹션 전용 - 항상 해당 공동구매(/deals/{id})로
// 링크한다(활성 공동구매만 걸러서 내려오므로 groupBuyId는 항상 존재).
export function ProductSearchCard({
  item,
  variant,
}: {
  item: ProductSearchResponse;
  variant: ProductSearchCardVariant;
}) {
  const [thumbnailFailed, setThumbnailFailed] = useState(false);
  const isGroupBuy = variant === "groupBuy";
  const daysLeft = isGroupBuy ? toDaysLeft(item.endAt) : null;
  const price = isGroupBuy ? (item.currentUnitPrice ?? item.startPrice) : item.startPrice;
  const href = isGroupBuy && item.groupBuyId ? `/deals/${item.groupBuyId}` : `/products/${item.id}`;
  const catalog = resolveCatalogEntry(item.productName);

  return (
    <Link href={href} className="group block">
      <div className="relative">
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
        {daysLeft !== null && (
          <div className="absolute left-2.5 top-2.5">
            <Tag highlighted>D-{daysLeft}</Tag>
          </div>
        )}
      </div>
      <div className="mt-3 space-y-1">
        <p className="line-clamp-2 min-h-12 text-base">{item.productName}</p>
        <p className="text-sm font-bold">{won(price)}</p>
      </div>
      {isGroupBuy && (
        <div className="mt-2 flex items-center justify-end">
          <span className="text-xs font-bold text-wb-green">
            {(item.currentQuantity ?? 0).toLocaleString("ko-KR")}개 참여
          </span>
        </div>
      )}
    </Link>
  );
}
