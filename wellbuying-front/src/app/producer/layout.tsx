"use client";

import { BarChart3, Heart, Package, Settings, ShoppingBag, ShoppingCart, Users, Wallet } from "lucide-react";
import { RequireRole } from "@/components/auth/RequireRole";
import { AppShell, type NavItem } from "@/components/shell/AppShell";

const NAV_ITEMS: NavItem[] = [
  { href: "/producer/dashboard", label: "생산자 홈", icon: BarChart3 },
  { href: "/producer/products", label: "상품 관리", icon: Package },
  { href: "/producer/deals", label: "공동구매", icon: Users },
  { href: "/producer/settlements", label: "정산 내역", icon: Wallet },
];

const WORKSPACE_LINKS: NavItem[] = [{ href: "/home", label: "소비자 모드", icon: ShoppingCart }];

// 찜/참여 내역/내 정보는 사이드바 대신 헤더 계정 메뉴에 모아 두고, 클릭하면 내 정보·찜·참여 내역이 같은 계정 페이지 안에서 탭으로 묶여 보이게 한다.
const ACCOUNT_LINKS: NavItem[] = [
  { href: "/favorites", label: "찜", icon: Heart },
  { href: "/orders", label: "참여 내역", icon: ShoppingBag },
  { href: "/profile", label: "내 정보", icon: Settings },
];

export default function ProducerLayout({ children }: { children: React.ReactNode }) {
  return (
    <RequireRole role="SELLER">
      <AppShell
        title="생산자"
        navItems={NAV_ITEMS}
        workspaceLinks={WORKSPACE_LINKS}
        accountLinks={ACCOUNT_LINKS}
        layout="sidebar"
      >
        {children}
      </AppShell>
    </RequireRole>
  );
}
