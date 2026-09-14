import { ShoppingBag } from "lucide-react";

// 즐겨찾기 아이콘(app/icon.svg)과 같은 디자인 — 헤더 타이틀 옆에 붙여 브랜드 마크로 재사용한다
export function BrandMark({ className = "h-7 w-7" }: { className?: string }) {
  return (
    <span className={`flex shrink-0 items-center justify-center rounded-lg bg-wb-green text-white ${className}`}>
      <ShoppingBag className="h-4 w-4" strokeWidth={2} />
    </span>
  );
}
