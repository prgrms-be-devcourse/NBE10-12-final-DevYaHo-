"use client";

import { LayoutDashboard, Package, Settings, ShieldCheck, ShoppingCart, UserCheck, Users, Wallet } from "lucide-react";
import { RequireRole } from "@/components/auth/RequireRole";
import { AppShell, type NavItem } from "@/components/shell/AppShell";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";

const WORKSPACE_LINKS: NavItem[] = [{ href: "/home", label: "소비자 모드", icon: ShoppingCart }];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { pendingReviewCount, readySettlementCount } = useDemoStore();

  const navItems: NavItem[] = [
    { href: "/admin/dashboard", label: "대시보드", icon: LayoutDashboard },
    { href: "/admin/deals", label: "공동구매 관리", icon: Package },
    { href: "/admin/reviews", label: "상품 심사", icon: ShieldCheck, badge: pendingReviewCount },
    { href: "/admin/sellers", label: "판매자 승인", icon: UserCheck },
    { href: "/admin/settlements", label: "정산 관리", icon: Wallet, badge: readySettlementCount },
    { href: "/admin/members", label: "회원 현황", icon: Users },
    { href: "/profile", label: "내 정보", icon: Settings },
  ];

  return (
    <RequireRole role="ADMIN">
      <AppShell title="관리자" navItems={navItems} workspaceLinks={WORKSPACE_LINKS} layout="sidebar">
        {children}
      </AppShell>
    </RequireRole>
  );
}
