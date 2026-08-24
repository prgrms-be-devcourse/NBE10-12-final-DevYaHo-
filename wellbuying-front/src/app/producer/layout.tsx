"use client";

import { BarChart3, RadioTower, Settings, ShoppingCart, Users, Wallet } from "lucide-react";
import { RequireRole } from "@/components/auth/RequireRole";
import { AppShell, type NavItem } from "@/components/shell/AppShell";

const NAV_ITEMS: NavItem[] = [
  { href: "/producer/dashboard", label: "생산자 홈", icon: BarChart3 },
  { href: "/producer/deals", label: "공동구매", icon: Users },
  { href: "/producer/live-groupbuys", label: "라이브 공동구매", icon: RadioTower },
  { href: "/producer/settlements", label: "정산 내역", icon: Wallet },
  { href: "/profile", label: "설정", icon: Settings },
];

const WORKSPACE_LINKS: NavItem[] = [{ href: "/home", label: "소비자 모드", icon: ShoppingCart }];

export default function ProducerLayout({ children }: { children: React.ReactNode }) {
  return (
    <RequireRole role="SELLER">
      <AppShell title="생산자" navItems={NAV_ITEMS} workspaceLinks={WORKSPACE_LINKS}>
        {children}
      </AppShell>
    </RequireRole>
  );
}
