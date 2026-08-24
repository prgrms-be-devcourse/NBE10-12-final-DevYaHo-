"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { LucideIcon } from "lucide-react";
import { useAuth } from "@/lib/auth/AuthProvider";

export type NavItem = {
  href: string;
  label: string;
  icon: LucideIcon;
  badge?: number;
};

export function AppShell({
  title,
  navItems,
  workspaceLinks,
  children,
}: {
  title: string;
  navItems: NavItem[];
  workspaceLinks?: NavItem[];
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const { member } = useAuth();

  return (
    <div className="flex min-h-0 flex-1">
      <aside className="hidden w-56 shrink-0 flex-col justify-between border-r border-wb-line bg-wb-surface/60 py-5 md:flex">
        <div>
          <p className="px-5 pb-4 text-sm font-bold text-wb-green">{title}</p>
          <nav className="space-y-0.5 px-2.5">
            {navItems.map((item) => {
              const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`flex items-center justify-between rounded-lg px-3 py-2 text-sm font-semibold transition-colors ${
                    active ? "bg-wb-light-green/60 text-wb-green" : "text-wb-ink hover:bg-wb-canvas"
                  }`}
                >
                  <span className="flex items-center gap-2.5">
                    <Icon className="h-4 w-4" strokeWidth={2} />
                    {item.label}
                  </span>
                  {!!item.badge && (
                    <span className="rounded-full bg-wb-orange/15 px-1.5 py-0.5 text-[10px] font-bold text-wb-orange">
                      {item.badge}
                    </span>
                  )}
                </Link>
              );
            })}
          </nav>

          {workspaceLinks && workspaceLinks.length > 0 && (
            <>
              <p className="px-5 pb-2 pt-6 text-xs font-bold text-wb-secondary">작업 공간</p>
              <nav className="space-y-0.5 px-2.5">
                {workspaceLinks.map((item) => {
                  const Icon = item.icon;
                  return (
                    <Link
                      key={item.href}
                      href={item.href}
                      className="flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-semibold text-wb-ink hover:bg-wb-canvas"
                    >
                      <Icon className="h-4 w-4" strokeWidth={2} />
                      {item.label}
                    </Link>
                  );
                })}
              </nav>
            </>
          )}
        </div>

        {member && (
          <div className="mx-2.5 rounded-lg bg-wb-canvas p-3">
            <p className="truncate text-xs font-bold">{member.name}</p>
            <p className="truncate text-[11px] text-wb-secondary">{member.email}</p>
          </div>
        )}
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <nav className="flex gap-1 overflow-x-auto border-b border-wb-line bg-wb-surface/60 px-3 py-2 md:hidden">
          {navItems.map((item) => {
            const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`shrink-0 rounded-full px-3 py-1.5 text-xs font-semibold ${
                  active ? "bg-wb-green text-white" : "bg-wb-canvas text-wb-secondary"
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
        <main className="min-w-0 flex-1 overflow-y-auto">{children}</main>
      </div>
    </div>
  );
}
