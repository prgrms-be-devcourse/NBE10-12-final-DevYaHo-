"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { Package, Plus, Radio } from "lucide-react";
import { GroupBuyArtwork } from "@/components/deal/GroupBuyArtwork";
import { GroupBuyCreateModal } from "@/components/producer/GroupBuyCreateModal";
import { SuspensionRequestModal } from "@/components/producer/SuspensionRequestModal";
import { BarChart, type BarChartPoint } from "@/components/ui/BarChart";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { MetricCard } from "@/components/ui/MetricCard";
import { StatusPill } from "@/components/ui/Tag";
import { listMyGroupBuys } from "@/lib/api/groupBuy";
import { ApiError } from "@/lib/api/http";
import { listMyProducts } from "@/lib/api/product";
import type { GroupBuySummaryResponse, ProductMineResponse } from "@/lib/api/types";
import { resolveCatalogEntry } from "@/lib/groupBuy/seedCatalog";

const CHART_DAYS = 30;

const PRODUCT_STATUS_LABEL: Record<ProductMineResponse["status"], string> = {
  PENDING: "심사 대기",
  APPROVED: "승인됨",
  REJECTED: "반려됨",
};

const PRODUCT_STATUS_TONE: Record<ProductMineResponse["status"], "orange" | "green" | "red"> = {
  PENDING: "orange",
  APPROVED: "green",
  REJECTED: "red",
};

// 참여자 수(participantCount)와 가격 구간(priceTiers)은 목록 응답에 없고, 항목마다 상세/상태 API를
// 따로 호출해야 얻을 수 있었다(N+1). 백엔드에 대시보드용 요약 API가 추가되기 전까지는 목록 응답만으로
// 보여줄 수 있는 정보로 대시보드를 단순화한다 - 전체 참여자 수·예상 매출 카드는 뺐다.
function buildMonthlyChart(deals: GroupBuySummaryResponse[]): BarChartPoint[] {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const points: BarChartPoint[] = [];

  for (let i = CHART_DAYS - 1; i >= 0; i -= 1) {
    const day = new Date(today);
    day.setDate(day.getDate() - i);
    const dayEnd = new Date(day);
    dayEnd.setDate(dayEnd.getDate() + 1);

    const value = deals.reduce((sum, deal) => {
      const start = new Date(deal.startAt);
      const end = new Date(deal.endAt);
      const activeThatDay = start < dayEnd && end >= day;
      return activeThatDay ? sum + deal.currentQuantity : sum;
    }, 0);

    points.push({ label: `${day.getMonth() + 1}/${day.getDate()}`, value });
  }

  return points;
}

export default function ProducerDashboardPage() {
  const [deals, setDeals] = useState<GroupBuySummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [suspensionTargetId, setSuspensionTargetId] = useState<number | null>(null);

  const [products, setProducts] = useState<ProductMineResponse[]>([]);
  const [productsLoading, setProductsLoading] = useState(true);
  const [productsError, setProductsError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await listMyGroupBuys({ size: 100 });
      setDeals(page.content);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "현황을 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    async function load() {
      await reload();
    }
    load();
  }, [reload]);

  useEffect(() => {
    let ignore = false;
    listMyProducts()
      .then((slice) => {
        if (!ignore) setProducts(slice.content);
      })
      .catch((e) => {
        if (ignore) return;
        setProductsError(e instanceof ApiError ? e.message : "상품 현황을 불러오지 못했어요.");
      })
      .finally(() => {
        if (!ignore) setProductsLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, []);

  const recruitingCount = deals.filter((deal) => deal.status === "ONGOING").length;
  const chartData = useMemo(() => buildMonthlyChart(deals), [deals]);
  const approvedProductCount = useMemo(() => products.filter((p) => p.status === "APPROVED").length, [products]);
  const pendingProductCount = useMemo(() => products.filter((p) => p.status === "PENDING").length, [products]);

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">생산자 홈</h1>
          <p className="mt-1 text-sm text-wb-secondary">가격을 투명하게 설계하고 공동구매를 운영하세요.</p>
        </div>
        <Button onClick={() => setShowCreate(true)}>
          <Plus className="h-4 w-4" /> 새 공동구매
        </Button>
      </div>

      {error && <Banner tone="error">{error}</Banner>}

      {loading ? (
        <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : (
        <>
          <div className="max-w-xs">
            <MetricCard icon={Radio} title="운영 중" value={`${recruitingCount}건`} />
          </div>

          <div className="rounded-2xl border border-wb-line bg-wb-surface p-6">
            <h2 className="text-lg font-bold">최근 {CHART_DAYS}일 참여 수량</h2>
            <p className="mt-1 text-xs text-wb-secondary">
              보유 공동구매의 그 날 기준 누적 참여 수량 합계예요. 일별 신규 참여 추이(정확한 유입량)는
              별도 집계 API가 필요해 이번엔 제공하지 않아요.
            </p>
            <div className="mt-4">
              <BarChart data={chartData} />
            </div>
          </div>

          <div className="rounded-2xl border border-wb-line bg-wb-surface p-6">
            <h2 className="text-lg font-bold">내 공동구매 현황</h2>
            {deals.length === 0 ? (
              <p className="mt-4 text-sm text-wb-secondary">아직 개설한 공동구매가 없어요.</p>
            ) : (
              <div className="mt-4 divide-y divide-wb-line">
                {deals.slice(0, 3).map((deal) => {
                  const catalog = resolveCatalogEntry(deal.productName);
                  return (
                    <div key={deal.id} className="flex items-center gap-4 py-3.5 first:pt-0 last:pb-0">
                      <Link href={`/producer/deals/${deal.id}`} className="flex min-w-0 flex-1 items-center gap-4 hover:opacity-80">
                        <GroupBuyArtwork entry={catalog} className="h-16 w-20 shrink-0" />
                        <div className="min-w-0 flex-1">
                          <div className="mb-1 flex items-center gap-1.5">
                            <p className="line-clamp-1 text-sm font-bold">{deal.title}</p>
                            {deal.suspended && <StatusPill tone="red">정지됨</StatusPill>}
                          </div>
                          <p className="text-xs text-wb-secondary">
                            {deal.currentQuantity.toLocaleString("ko-KR")}개 참여
                          </p>
                        </div>
                      </Link>
                      {deal.status === "ONGOING" && !deal.suspended && (
                        <button
                          onClick={() => setSuspensionTargetId(deal.id)}
                          className="shrink-0 text-xs font-semibold text-red-600"
                        >
                          판매정지 요청
                        </button>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          <div className="rounded-2xl border border-wb-line bg-wb-surface p-6">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold">내 상품 현황</h2>
              <Link href="/producer/products" className="text-xs font-bold text-wb-green">
                전체 보기
              </Link>
            </div>

            {productsError && (
              <div className="mt-4">
                <Banner tone="error">{productsError}</Banner>
              </div>
            )}

            {productsLoading ? (
              <p className="py-8 text-center text-sm text-wb-secondary">불러오는 중...</p>
            ) : (
              <>
                <div className="mt-4 grid grid-cols-2 gap-4">
                  <MetricCard icon={Package} title="승인된 상품" value={`${approvedProductCount}개`} />
                  <MetricCard icon={Package} title="심사 대기 상품" value={`${pendingProductCount}개`} detail={pendingProductCount > 0 ? "확인 필요" : undefined} />
                </div>

                {products.length === 0 ? (
                  <p className="mt-4 text-sm text-wb-secondary">아직 등록한 상품이 없어요.</p>
                ) : (
                  <div className="mt-4 divide-y divide-wb-line">
                    {products.slice(0, 3).map((product) => (
                      <div key={product.id} className="flex items-center gap-3 py-3 first:pt-0 last:pb-0">
                        <div className="min-w-0 flex-1">
                          <p className="line-clamp-1 text-sm font-bold">{product.productName}</p>
                          <p className="text-xs text-wb-secondary">{product.startPrice.toLocaleString("ko-KR")}원</p>
                        </div>
                        <StatusPill tone={PRODUCT_STATUS_TONE[product.status]}>
                          {PRODUCT_STATUS_LABEL[product.status]}
                        </StatusPill>
                      </div>
                    ))}
                  </div>
                )}
              </>
            )}
          </div>
        </>
      )}

      <GroupBuyCreateModal open={showCreate} onClose={() => setShowCreate(false)} onCreated={reload} />

      <SuspensionRequestModal
        open={suspensionTargetId !== null}
        groupBuyId={suspensionTargetId}
        onClose={() => setSuspensionTargetId(null)}
        onRequested={reload}
      />
    </div>
  );
}
