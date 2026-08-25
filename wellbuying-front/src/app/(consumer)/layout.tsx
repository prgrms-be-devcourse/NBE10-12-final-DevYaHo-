"use client";

import { Heart, Leaf, Settings, ShoppingBag } from "lucide-react";
import { RequireAuth } from "@/components/auth/RequireAuth";
import { AppShell, type NavItem } from "@/components/shell/AppShell";
import { useAuth } from "@/lib/auth/AuthProvider";

const NAV_ITEMS: NavItem[] = [];

const ACCOUNT_LINKS: NavItem[] = [
  { href: "/favorites", label: "찜", icon: Heart },
  { href: "/orders", label: "참여 내역", icon: ShoppingBag },
  { href: "/profile", label: "설정", icon: Settings },
];

export default function ConsumerLayout({ children }: { children: React.ReactNode }) {
  const { member } = useAuth();
  // 관리자 모드는 UI에 노출하지 않음 - 관리자는 /admin 경로로 직접 접근
  // 창작자센터 버튼은 누구에게나 보이되, SELLER만 실제 대시보드로 들어가고 그 외는 창작자 신청 페이지로 안내
  const workspaceLinks: NavItem[] = [
    {
      href: member?.role === "SELLER" ? "/producer/dashboard" : "/seller/apply",
      label: "창작자센터",
      icon: Leaf,
    },
  ];

  return (
    <RequireAuth>
      <AppShell
        title="WellBuying"
        titleHref="/home"
        navItems={NAV_ITEMS}
        workspaceLinks={workspaceLinks}
        accountLinks={ACCOUNT_LINKS}
      >
        {children}
      </AppShell>
    </RequireAuth>
  );
}
