"use client";

import { Heart, LayoutDashboard, Leaf, Settings, ShoppingBag } from "lucide-react";
import { AppShell, type NavItem } from "@/components/shell/AppShell";
import { BrandMark } from "@/components/shell/BrandMark";
import { HeaderSearchBar } from "@/components/shell/HeaderSearchBar";
import { CategoryHoverTab } from "@/components/consumer/CategoryHoverTab";
import { useAuth } from "@/lib/auth/AuthProvider";

// 찜/참여 내역/내 정보는 각각 /favorites, /orders, /profile로 이동한다 -
// 이 계정 드롭다운은 홈/탐색/랭킹 같은 둘러보기 화면에서 그 개인 영역으로 바로 넘어가는 용도로만 남긴다.
const NAV_ITEMS: NavItem[] = [];

const BASE_ACCOUNT_LINKS: NavItem[] = [
  { href: "/favorites", label: "찜", icon: Heart },
  { href: "/orders", label: "결제 내역", icon: ShoppingBag },
  { href: "/profile", label: "내 정보", icon: Settings },
];

// 관리자는 소비자 활동(찜/참여 내역)이 의미가 없으므로 관리자 페이지만 노출
const ADMIN_ACCOUNT_LINKS: NavItem[] = [
  { href: "/admin/dashboard", label: "관리자 페이지", icon: LayoutDashboard },
];

export default function ConsumerLayout({ children }: { children: React.ReactNode }) {
  const { member } = useAuth();
  // 판매자공간 버튼은 관리자를 제외하고 노출. SELLER면 대시보드, 그 외(BUYER 등)는 신청 페이지
  const workspaceLinks: NavItem[] = member?.role === "ADMIN" ? [] : [
    {
      href: member?.role === "SELLER" ? "/producer/dashboard" : "/seller/apply",
      label: "판매자공간",
      icon: Leaf,
    },
  ];

  const accountLinks = member?.role === "ADMIN" ? ADMIN_ACCOUNT_LINKS : BASE_ACCOUNT_LINKS;

  return (
    <AppShell
      title="WellBuying"
      titleHref="/"
      titleIcon={<BrandMark />}
      navItems={NAV_ITEMS}
      workspaceLinks={workspaceLinks}
      accountLinks={accountLinks}
      categorySlot={<CategoryHoverTab />}
      searchSlot={<HeaderSearchBar />}
    >
      {children}
    </AppShell>
  );
}
