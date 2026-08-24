"use client";

import { Heart, Home, LayoutGrid, Leaf, RadioTower, Settings, Shield, ShoppingBag } from "lucide-react";
import { RequireAuth } from "@/components/auth/RequireAuth";
import { AppShell, type NavItem } from "@/components/shell/AppShell";
import { useAuth } from "@/lib/auth/AuthProvider";

const NAV_ITEMS: NavItem[] = [
  { href: "/home", label: "홈", icon: Home },
  { href: "/explore", label: "둘러보기", icon: LayoutGrid },
  { href: "/live-groupbuys", label: "라이브 공동구매", icon: RadioTower },
  { href: "/favorites", label: "찜", icon: Heart },
  { href: "/orders", label: "참여 내역", icon: ShoppingBag },
  { href: "/profile", label: "설정", icon: Settings },
];

export default function ConsumerLayout({ children }: { children: React.ReactNode }) {
  const { member } = useAuth();
  const workspaceLinks: NavItem[] = [];
  if (member?.role === "SELLER") {
    workspaceLinks.push({ href: "/producer/dashboard", label: "생산자 모드", icon: Leaf });
  }
  if (member?.role === "ADMIN") {
    workspaceLinks.push({ href: "/admin/dashboard", label: "관리자 모드", icon: Shield });
  }

  return (
    <RequireAuth>
      <AppShell title="WellBuying" navItems={NAV_ITEMS} workspaceLinks={workspaceLinks}>
        {children}
      </AppShell>
    </RequireAuth>
  );
}
