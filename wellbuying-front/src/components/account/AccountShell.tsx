"use client";

import { BarChart3, Heart, Settings, ShoppingBag, ShoppingCart } from "lucide-react";
import { RequireAuth } from "@/components/auth/RequireAuth";
import { AppShell, type NavItem } from "@/components/shell/AppShell";
import { useAuth } from "@/lib/auth/AuthProvider";

const ACCOUNT_LINKS: NavItem[] = [
  { href: "/profile", label: "내 정보", icon: Settings },
  { href: "/favorites", label: "찜", icon: Heart },
  { href: "/orders", label: "참여 내역", icon: ShoppingBag },
];

// 내 정보/찜/참여 내역 세 페이지가 공유하는 셸.
// 생산자·관리자 대시보드 사이드바를 재사용하지 않고 항상 동일한 "개인 영역"으로 보이게 하되,
// 왼쪽 사이드바로 세 메뉴를 두고 본문은 흰 배경(bg-wb-surface, AppShell의 sidebar 레이아웃 기본값)으로 둘러보기 화면과 구분한다.
export function AccountShell({ children }: { children: React.ReactNode }) {
  const { member } = useAuth();

  const backLink =
    member?.role === "SELLER"
      ? { href: "/producer/dashboard", label: "대시보드로", icon: BarChart3 }
      : member?.role === "ADMIN"
        ? { href: "/admin/dashboard", label: "대시보드로", icon: BarChart3 }
        : { href: "/home", label: "홈으로", icon: ShoppingCart };

  // 관리자는 소비자 활동(찜/참여 내역)이 의미가 없으므로 내 정보만 노출
  const accountItems = member?.role === "ADMIN" ? ACCOUNT_LINKS.filter((item) => item.href === "/profile") : ACCOUNT_LINKS;

  return (
    <RequireAuth>
      <AppShell
        title="WellBuying"
        titleHref="/home"
        navItems={accountItems}
        workspaceLinks={[backLink]}
        accountLinks={accountItems}
        layout="sidebar"
      >
        <div className="mx-auto max-w-3xl px-8 py-9">{children}</div>
      </AppShell>
    </RequireAuth>
  );
}
