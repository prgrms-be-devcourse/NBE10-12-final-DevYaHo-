"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { ChevronDown, type LucideIcon } from "lucide-react";
import { useAuth } from "@/lib/auth/AuthProvider";

export type NavItem = {
  href: string;
  label: string;
  icon: LucideIcon;
  badge?: number;
};

export function AppShell({
  title,
  titleHref,
  navItems,
  workspaceLinks,
  accountLinks,
  children,
}: {
  title: string;
  titleHref?: string;
  navItems: NavItem[];
  workspaceLinks?: NavItem[];
  accountLinks?: NavItem[];
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const { member } = useAuth();
  const [accountMenuOpen, setAccountMenuOpen] = useState(false);

  const titleEl = titleHref ? (
    <Link href={titleHref} className="text-sm font-extrabold tracking-tight text-wb-green">
      {title}
    </Link>
  ) : (
    <span className="text-sm font-extrabold tracking-tight text-wb-green">{title}</span>
  );

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <header className="sticky top-0 z-10 border-b border-wb-line bg-wb-surface/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-2 px-6 py-3">
          {titleEl}

          <nav className="flex flex-1 flex-wrap items-center gap-1.5">
            {navItems.map((item) => {
              const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-semibold transition-colors ${
                    active ? "bg-wb-green text-white" : "text-wb-ink hover:bg-wb-canvas"
                  }`}
                >
                  <Icon className="h-3.5 w-3.5" strokeWidth={2} />
                  {item.label}
                  {!!item.badge && (
                    <span
                      className={`rounded-full px-1.5 py-0.5 text-[10px] font-bold ${
                        active ? "bg-white/20 text-white" : "bg-wb-orange/15 text-wb-orange"
                      }`}
                    >
                      {item.badge}
                    </span>
                  )}
                </Link>
              );
            })}
          </nav>

          <div className="flex flex-wrap items-center gap-1.5">
            {workspaceLinks?.map((item) => {
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className="flex items-center gap-1.5 rounded-full border border-wb-line px-3 py-1.5 text-xs font-semibold text-wb-ink hover:bg-wb-canvas"
                >
                  <Icon className="h-3.5 w-3.5" strokeWidth={2} />
                  {item.label}
                </Link>
              );
            })}

            {member && (
              <div className="relative hidden sm:block">
                <button
                  onClick={() => setAccountMenuOpen((v) => !v)}
                  className="flex items-center gap-1.5 rounded-lg px-2 py-1 hover:bg-wb-canvas"
                >
                  <div className="text-right leading-tight">
                    <p className="max-w-32 truncate text-xs font-bold">{member.name}</p>
                    <p className="max-w-32 truncate text-[11px] text-wb-secondary">{member.email}</p>
                  </div>
                  <ChevronDown className="h-3.5 w-3.5 shrink-0 text-wb-secondary" />
                </button>

                {accountMenuOpen && accountLinks && accountLinks.length > 0 && (
                  <>
                    <button
                      aria-label="닫기"
                      onClick={() => setAccountMenuOpen(false)}
                      className="fixed inset-0 z-10 cursor-default"
                    />
                    <div className="absolute right-0 top-full z-20 mt-1 w-44 space-y-0.5 rounded-xl border border-wb-line bg-wb-surface p-1.5 shadow-md">
                      {accountLinks.map((item) => {
                        const Icon = item.icon;
                        return (
                          <Link
                            key={item.href}
                            href={item.href}
                            onClick={() => setAccountMenuOpen(false)}
                            className="flex items-center gap-2 rounded-lg px-3 py-2 text-left text-sm font-semibold text-wb-ink hover:bg-wb-canvas"
                          >
                            <Icon className="h-4 w-4" strokeWidth={2} />
                            {item.label}
                            {!!item.badge && (
                              <span className="ml-auto rounded-full bg-wb-orange/15 px-1.5 py-0.5 text-[10px] font-bold text-wb-orange">
                                {item.badge}
                              </span>
                            )}
                          </Link>
                        );
                      })}
                    </div>
                  </>
                )}
              </div>
            )}
          </div>
        </div>
      </header>

      <main className="min-w-0 flex-1 overflow-y-auto">{children}</main>
    </div>
  );
}
