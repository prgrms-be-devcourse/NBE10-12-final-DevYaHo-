"use client";

import { useEffect, useState } from "react";
import { LayoutDashboard, Package, Settings, ShieldCheck, ShoppingCart, Tag, UserCheck, Wallet } from "lucide-react";
import { RequireRole } from "@/components/auth/RequireRole";
import { AppShell, type NavItem } from "@/components/shell/AppShell";
import { ToastViewport } from "@/components/ui/ToastViewport";
import { getAdminSettlementSummary, listAdminProducts } from "@/lib/api/admin";

const WORKSPACE_LINKS: NavItem[] = [{ href: "/", label: "소비자 모드", icon: ShoppingCart }];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const [pendingReviewCount, setPendingReviewCount] = useState(0);
  const [pendingSettlementCount, setPendingSettlementCount] = useState(0);

  useEffect(() => {
    let ignore = false;
    listAdminProducts({ status: "PENDING", size: 1 })
      .then((response) => {
        if (!ignore) setPendingReviewCount(response.page.totalElements);
      })
      .catch(() => {
        if (!ignore) setPendingReviewCount(0);
      });
    return () => {
      ignore = true;
    };
  }, []);

  useEffect(() => {
    let ignore = false;
    getAdminSettlementSummary()
      .then((res) => {
        if (!ignore) setPendingSettlementCount(res.pendingCount);
      })
      .catch(() => {
        if (!ignore) setPendingSettlementCount(0);
      });
    return () => {
      ignore = true;
    };
  }, []);

  const navItems: NavItem[] = [
    { href: "/admin/dashboard", label: "대시보드", icon: LayoutDashboard },
    { href: "/admin/reviews", label: "상품 관리", icon: ShieldCheck, badge: pendingReviewCount },
    { href: "/admin/deals", label: "공동구매 관리", icon: Package },
    { href: "/admin/sellers", label: "회원 관리", icon: UserCheck },
    { href: "/admin/settlements", label: "정산 관리", icon: Wallet, badge: pendingSettlementCount },
    { href: "/admin/categories", label: "카테고리 관리", icon: Tag },
    { href: "/profile", label: "내 정보", icon: Settings },
  ];

  return (
    <RequireRole role="ADMIN">
      <AppShell title="관리자" navItems={navItems} workspaceLinks={WORKSPACE_LINKS} layout="sidebar">
        {children}
      </AppShell>
      <ToastViewport />
    </RequireRole>
  );
}
