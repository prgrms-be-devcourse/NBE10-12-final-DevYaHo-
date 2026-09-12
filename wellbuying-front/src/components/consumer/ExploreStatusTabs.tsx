"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";

type Tab = {
  label: string;
  href: string;
  isActive: (pathname: string, searchParams: URLSearchParams) => boolean;
};

const TABS: Tab[] = [
  {
    label: "진행중",
    href: "/explore",
    isActive: (pathname, sp) => pathname === "/explore" && sp.get("status") !== "scheduled" && !sp.get("sort"),
  },
  {
    label: "진행예정",
    href: "/explore?status=scheduled",
    isActive: (pathname, sp) => pathname === "/explore" && sp.get("status") === "scheduled",
  },
  {
    label: "인기",
    href: "/ranking",
    isActive: (pathname) => pathname === "/ranking",
  },
  {
    label: "신규",
    href: "/explore?sort=new",
    isActive: (pathname, sp) => pathname === "/explore" && sp.get("sort") === "new",
  },
  {
    label: "마감임박",
    href: "/explore?sort=closing",
    isActive: (pathname, sp) => pathname === "/explore" && sp.get("sort") === "closing",
  },
];

// 카테고리 탭 옆에 놓는 상태별 바로가기 탭
export function ExploreStatusTabs() {
  const pathname = usePathname();
  const searchParams = useSearchParams();

  return (
    <nav className="flex items-center gap-6">
      {TABS.map((tab) => {
        const active = tab.isActive(pathname, searchParams);
        return (
          <Link
            key={tab.label}
            href={tab.href}
            className={`text-sm font-semibold ${active ? "text-wb-ink" : "text-wb-secondary hover:text-wb-ink"}`}
          >
            {tab.label}
          </Link>
        );
      })}
    </nav>
  );
}
