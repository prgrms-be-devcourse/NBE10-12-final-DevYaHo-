"use client";

import { Package } from "lucide-react";
import { useState } from "react";

// 판매자가 등록한 외부 썸네일 URL. 없거나 로드에 실패하면 아이콘 플레이스홀더로 대체한다
export function OrderThumbnail({
  url,
  alt,
  className = "",
}: {
  url: string | null;
  alt: string;
  className?: string;
}) {
  const [failed, setFailed] = useState(false);

  if (!url || failed) {
    return (
      <div className={`flex items-center justify-center rounded-lg bg-wb-canvas text-wb-secondary ${className}`}>
        <Package className="h-6 w-6" strokeWidth={1.25} />
      </div>
    );
  }

  return (
    // eslint-disable-next-line @next/next/no-img-element -- 판매자가 등록한 외부 썸네일 URL이라 next/image 최적화 대상이 아님
    <img
      src={url}
      alt={alt}
      className={`rounded-lg object-cover ${className}`}
      onError={() => setFailed(true)}
    />
  );
}
