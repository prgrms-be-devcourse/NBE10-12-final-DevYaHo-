"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { LineChart, Plus, Radio, Users } from "lucide-react";
import { GroupBuyArtwork } from "@/components/deal/GroupBuyArtwork";
import { GroupBuyCreateModal } from "@/components/producer/GroupBuyCreateModal";
import { BarChart, type BarChartPoint } from "@/components/ui/BarChart";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { MetricCard } from "@/components/ui/MetricCard";
import { listGroupBuys, getGroupBuy, getGroupBuyStatus } from "@/lib/api/groupBuy";
import { ApiError } from "@/lib/api/http";
import type { GroupBuyDetailResponse, GroupBuyStatusResponse } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/AuthProvider";
import { compactCount, compactWon, won } from "@/lib/format";
import { resolveCatalogEntry } from "@/lib/groupBuy/seedCatalog";
import { resolveCurrentUnitPrice } from "@/lib/groupBuyPricing";

type DealRow = { detail: GroupBuyDetailResponse; status: GroupBuyStatusResponse };

const CHART_DAYS = 30;

function buildMonthlyChart(rows: DealRow[]): BarChartPoint[] {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const points: BarChartPoint[] = [];

  for (let i = CHART_DAYS - 1; i >= 0; i -= 1) {
    const day = new Date(today);
    day.setDate(day.getDate() - i);
    const dayEnd = new Date(day);
    dayEnd.setDate(dayEnd.getDate() + 1);

    const value = rows.reduce((sum, row) => {
      const start = new Date(row.detail.startAt);
      const end = new Date(row.detail.endAt);
      const activeThatDay = start < dayEnd && end >= day;
      return activeThatDay ? sum + row.status.currentQuantity : sum;
    }, 0);

    points.push({ label: `${day.getMonth() + 1}/${day.getDate()}`, value });
  }

  return points;
}

export default function ProducerDashboardPage() {
  const { member } = useAuth();
  const [rows, setRows] = useState<DealRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await listGroupBuys({ size: 100 });
      const own = page.content.filter((item) => item.producerId === member?.memberId);
      const loaded = await Promise.all(
        own.map(async (item) => {
          const [detail, status] = await Promise.all([getGroupBuy(item.id), getGroupBuyStatus(item.id)]);
          return { detail, status };
        }),
      );
      setRows(loaded);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "현황을 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  }, [member?.memberId]);

  useEffect(() => {
    async function load() {
      await reload();
    }
    load();
  }, [reload]);

  const recruitingCount = rows.filter((row) => row.status.status === "ONGOING").length;
  const totalParticipants = rows.reduce((sum, row) => sum + row.status.participantCount, 0);
  const estimatedSales = rows.reduce((sum, row) => {
    const price = resolveCurrentUnitPrice(row.detail.priceTiers, row.status.currentQuantity) ?? 0;
    return sum + price * row.status.currentQuantity;
  }, 0);
  const chartData = buildMonthlyChart(rows);

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
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <MetricCard icon={Radio} title="운영 중" value={`${recruitingCount}건`} />
            <MetricCard icon={Users} title="전체 참여" value={compactCount(totalParticipants, "명")} />
            <MetricCard icon={LineChart} title="예상 매출" value={compactWon(estimatedSales)} />
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
            {rows.length === 0 ? (
              <p className="mt-4 text-sm text-wb-secondary">아직 개설한 공동구매가 없어요.</p>
            ) : (
              <div className="mt-4 divide-y divide-wb-line">
                {rows.slice(0, 3).map(({ detail, status }) => {
                  const catalog = resolveCatalogEntry(detail.productName);
                  const price = resolveCurrentUnitPrice(detail.priceTiers, status.currentQuantity);
                  return (
                    <Link
                      key={detail.id}
                      href={`/producer/deals/${detail.id}`}
                      className="flex items-center gap-4 py-3.5 first:pt-0 last:pb-0 hover:opacity-80"
                    >
                      <GroupBuyArtwork entry={catalog} className="h-16 w-20 shrink-0" />
                      <div className="min-w-0 flex-1">
                        <p className="line-clamp-1 text-sm font-bold">{detail.title}</p>
                        <p className="text-xs text-wb-secondary">
                          {status.currentQuantity.toLocaleString("ko-KR")}개 · {price !== null ? won(price) : "-"}
                        </p>
                      </div>
                    </Link>
                  );
                })}
              </div>
            )}
          </div>
        </>
      )}

      <GroupBuyCreateModal open={showCreate} onClose={() => setShowCreate(false)} onCreated={reload} />
    </div>
  );
}
