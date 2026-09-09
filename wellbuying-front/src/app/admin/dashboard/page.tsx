"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ChevronRight, Package, PauseCircle, ShoppingBag, Store } from "lucide-react";
import { Banner } from "@/components/ui/Banner";
import { EmptyState } from "@/components/ui/EmptyState";
import { MetricCard } from "@/components/ui/MetricCard";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { listAdminGroupBuys, listAdminProducts, listSellerApplications, listSuspensionRequests } from "@/lib/api/admin";
import { ApiError } from "@/lib/api/http";
import type { GroupBuySummaryResponse } from "@/lib/api/types";

type Metrics = {
  ongoingGroupBuys: number;
  pendingProducts: number;
  pendingSuspensions: number;
  pendingSellers: number;
};

const TODO_ITEMS = [
  {
    key: "products" as const,
    href: "/admin/reviews",
    icon: Package,
    title: "신규 상품 심사",
    description: "가격 구조와 생산 정보를 확인해주세요.",
  },
  {
    key: "suspensions" as const,
    href: "/admin/deals",
    icon: PauseCircle,
    title: "판매정지 승인 대기",
    description: "판매자가 요청한 판매정지 건을 검토해주세요.",
  },
  {
    key: "sellers" as const,
    href: "/admin/sellers",
    icon: Store,
    title: "셀러 가입 심사",
    description: "신규 셀러 가입 신청을 확인해주세요.",
  },
];

export default function AdminDashboardPage() {
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [ongoingDeals, setOngoingDeals] = useState<GroupBuySummaryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;

    Promise.all([
      listAdminGroupBuys({ status: "ONGOING", size: 5 }),
      listAdminProducts({ status: "PENDING", size: 1 }),
      listSuspensionRequests({ status: "PENDING", size: 1 }),
      listSellerApplications({ status: "PENDING", size: 1 }),
    ])
      .then(([groupBuys, products, suspensions, sellers]) => {
        if (ignore) return;
        setMetrics({
          ongoingGroupBuys: groupBuys.page.totalElements,
          pendingProducts: products.page.totalElements,
          pendingSuspensions: suspensions.page.totalElements,
          pendingSellers: sellers.page.totalElements,
        });
        setOngoingDeals(groupBuys.content);
      })
      .catch((e) => {
        if (ignore) return;
        setOngoingDeals([]);
        setError(e instanceof ApiError ? e.message : "대시보드 데이터를 불러오지 못했어요.");
      });

    return () => {
      ignore = true;
    };
  }, []);

  const todoCounts: Record<(typeof TODO_ITEMS)[number]["key"], number | null> = {
    products: metrics?.pendingProducts ?? null,
    suspensions: metrics?.pendingSuspensions ?? null,
    sellers: metrics?.pendingSellers ?? null,
  };

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-6 py-9">
      <div>
        <p className="text-xs font-bold tracking-wide text-wb-green">OVERVIEW</p>
        <h1 className="mt-1 text-3xl font-bold">운영 대시보드</h1>
        <p className="mt-1 text-sm text-wb-secondary">실시간 운영 현황</p>
      </div>

      {error && <Banner tone="error">{error}</Banner>}

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <MetricCard
          icon={ShoppingBag}
          title="진행 중 공동구매"
          value={metrics ? `${metrics.ongoingGroupBuys}건` : "-"}
        />
        <MetricCard icon={Package} title="상품 심사 대기" value={metrics ? `${metrics.pendingProducts}건` : "-"} detail="확인 필요" />
        <MetricCard
          icon={PauseCircle}
          title="판매정지 승인 대기"
          value={metrics ? `${metrics.pendingSuspensions}건` : "-"}
          detail="확인 필요"
        />
        <MetricCard icon={Store} title="셀러 가입 심사 대기" value={metrics ? `${metrics.pendingSellers}건` : "-"} detail="확인 필요" />
      </div>

      <div className="space-y-4 rounded-2xl border border-wb-line bg-wb-surface p-6">
        <div>
          <h2 className="text-lg font-bold">확인이 필요한 작업</h2>
          <p className="text-xs text-wb-secondary">우선순위가 높은 운영 업무예요.</p>
        </div>
        {TODO_ITEMS.map((item) => (
          <Link
            key={item.key}
            href={item.href}
            className="flex items-center gap-3.5 rounded-xl bg-wb-canvas p-3.5 hover:bg-wb-light-green/30"
          >
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-wb-green/12 text-wb-green">
              <item.icon className="h-4 w-4" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-bold">{item.title}</p>
              <p className="text-xs text-wb-secondary">{item.description}</p>
            </div>
            <span className="text-sm font-bold text-wb-green">
              {todoCounts[item.key] !== null ? `${todoCounts[item.key]}건` : "-"}
            </span>
            <ChevronRight className="h-4 w-4 text-wb-secondary" />
          </Link>
        ))}
      </div>

      <div className="space-y-4 rounded-2xl border border-wb-line bg-wb-surface p-6">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold">진행 중 공동구매</h2>
          <Link href="/admin/deals" className="text-xs font-bold text-wb-green">
            전체 보기
          </Link>
        </div>
        {ongoingDeals === null ? (
          <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>
        ) : ongoingDeals.length === 0 ? (
          <EmptyState icon={ShoppingBag} title="진행 중인 공동구매가 없어요" message="새로운 공동구매가 열리면 여기에 표시돼요." />
        ) : (
          <div className="divide-y divide-wb-line">
            {ongoingDeals.map((deal) => (
              <div key={deal.id} className="flex items-center gap-4 py-3.5 first:pt-0 last:pb-0">
                <div className="min-w-0 flex-1">
                  <p className="line-clamp-1 text-sm font-bold">{deal.title}</p>
                  <p className="text-xs text-wb-secondary">{deal.productName}</p>
                </div>
                <div className="hidden w-32 sm:block">
                  <ProgressBar value={deal.currentQuantity / deal.maxQuantity} />
                </div>
                <p className="w-24 shrink-0 text-right text-sm font-bold">
                  {deal.currentQuantity.toLocaleString("ko-KR")}/{deal.maxQuantity.toLocaleString("ko-KR")}개
                </p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
