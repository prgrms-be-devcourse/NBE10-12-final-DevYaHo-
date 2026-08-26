"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { CategoryHoverTab } from "@/components/consumer/CategoryHoverTab";

type SubNavItem = {
  label: string;
  href: string;
  isActive: (pathname: string, status: string | null, sort: string | null) => boolean;
};

// home/page.tsx 안에만 있던 서브 내비를 분리 — 탐색/랭킹으로 이동해도 사라지지 않도록 여러 페이지 상단에서 공유.
const ITEMS: SubNavItem[] = [
  { label: "홈", href: "/home", isActive: (p) => p === "/home" },
  {
    label: "진행중",
    href: "/explore",
    isActive: (p, status, sort) => p === "/explore" && status !== "scheduled" && !sort,
  },
  {
    label: "진행예정",
    href: "/explore?status=scheduled",
    isActive: (p, status) => p === "/explore" && status === "scheduled",
  },
  { label: "인기", href: "/ranking", isActive: (p) => p === "/ranking" },
  {
    label: "신규",
    href: "/explore?sort=new",
    isActive: (p, status, sort) => p === "/explore" && sort === "new",
  },
  {
    label: "마감임박",
    href: "/explore?sort=closing",
    isActive: (p, status, sort) => p === "/explore" && sort === "closing",
  },
];

export function DealsSubNav({
  categories,
  categoryValue,
  onCategoryChange,
}: {
  categories?: string[];
  categoryValue?: string;
  onCategoryChange?: (category: string) => void;
}) {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const status = searchParams.get("status");
  const sort = searchParams.get("sort");

  function renderItem(item: SubNavItem) {
    const active = item.isActive(pathname, status, sort);
    return (
      <Link
        key={item.label}
        href={item.href}
        className={`rounded-lg px-2.5 py-1.5 ${
          active ? "bg-wb-green text-white" : "hover:bg-wb-canvas hover:text-wb-ink"
        }`}
      >
        {item.label}
      </Link>
    );
  }

  return (
    <nav className="flex flex-wrap items-center gap-1 text-sm font-semibold text-wb-secondary">
      {renderItem(ITEMS[0])}
      {categories && categoryValue !== undefined && onCategoryChange && (
        <CategoryHoverTab categories={categories} value={categoryValue} onChange={onCategoryChange} />
      )}
      {ITEMS.slice(1).map(renderItem)}
    </nav>
  );
}
