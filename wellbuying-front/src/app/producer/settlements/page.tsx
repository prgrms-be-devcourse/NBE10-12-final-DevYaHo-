"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { CheckCircle2, ChevronLeft, ChevronRight, Clock, TrendingUp, Wallet } from "lucide-react";
import { SettlementDetailModal } from "@/components/producer/SettlementDetailModal";
import { SettlementTrendChart } from "@/components/producer/SettlementTrendChart";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { MetricCard } from "@/components/ui/MetricCard";
import { StatusPill } from "@/components/ui/Tag";
import { ApiError } from "@/lib/api/http";
import { getSettlementMonthlySummary, getSettlementTrend, listMySettlements } from "@/lib/api/settlement";
import type { SettlementListItemResponse, SettlementListStatus, SettlementMonthlySummaryResponse, SettlementTrendPointResponse } from "@/lib/api/types";
import { formatDateTime, won } from "@/lib/format";
import { SETTLEMENT_LIST_STATUS_LABEL, SETTLEMENT_LIST_STATUS_TONE } from "@/lib/settlement/settlementStatus";

const PAGE_SIZE = 10;

type StatusFilter = SettlementListStatus | "ALL";

const STATUS_TABS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "PENDING", label: "정산 대기중" },
  { value: "COMPLETED", label: "정산 완료" },
];

function changeRate(current: number, previous: number): string | undefined {
  if (previous <= 0) return undefined;
  const rate = Math.round(((current - previous) / previous) * 100);
  if (rate === 0) return "전월과 동일";
  return `전월 대비 ${rate > 0 ? "+" : ""}${rate}%`;
}

export default function ProducerSettlementsPage() {
  const now = useMemo(() => new Date(), []);
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const isCurrentMonth = year === now.getFullYear() && month === now.getMonth() + 1;

  const [settlements, setSettlements] = useState<SettlementListItemResponse[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [trend, setTrend] = useState<SettlementTrendPointResponse[]>([]);
  const [trendLoading, setTrendLoading] = useState(true);
  const [trendError, setTrendError] = useState<string | null>(null);

  const [summary, setSummary] = useState<SettlementMonthlySummaryResponse | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [summaryError, setSummaryError] = useState<string | null>(null);

  const [selected, setSelected] = useState<SettlementListItemResponse | null>(null);

  // 매출 추이/이번 달 요약은 월 네비게이션·상태 필터와 무관하게 항상 "지금"을 기준으로 한 번만 불러온다
  useEffect(() => {
    let cancelled = false;
    getSettlementTrend("MONTHLY")
      .then((res) => {
        if (!cancelled) setTrend(res);
      })
      .catch((e) => {
        if (!cancelled) setTrendError(e instanceof ApiError ? e.message : "매출 추이를 불러오지 못했어요.");
      })
      .finally(() => {
        if (!cancelled) setTrendLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    getSettlementMonthlySummary()
      .then((res) => {
        if (!cancelled) setSummary(res);
      })
      .catch((e) => {
        if (!cancelled) setSummaryError(e instanceof ApiError ? e.message : "이번 달 요약을 불러오지 못했어요.");
      })
      .finally(() => {
        if (!cancelled) setSummaryLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const loadFirstPage = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await listMySettlements({
        year,
        month,
        status: statusFilter === "ALL" ? undefined : statusFilter,
        page: 0,
        size: PAGE_SIZE,
      });
      setSettlements(res.content);
      setPage(res.page.number);
      setHasNext(res.page.number + 1 < res.page.totalPages);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "정산 내역을 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  }, [year, month, statusFilter]);

  useEffect(() => {
    void loadFirstPage();
  }, [loadFirstPage]);

  async function loadMore() {
    setLoadingMore(true);
    setError(null);
    try {
      const res = await listMySettlements({
        year,
        month,
        status: statusFilter === "ALL" ? undefined : statusFilter,
        page: page + 1,
        size: PAGE_SIZE,
      });
      setSettlements((prev) => [...prev, ...res.content]);
      setPage(res.page.number);
      setHasNext(res.page.number + 1 < res.page.totalPages);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "더 불러오지 못했어요.");
    } finally {
      setLoadingMore(false);
    }
  }

  function shiftMonth(delta: number) {
    const base = new Date(year, month - 1 + delta, 1);
    setYear(base.getFullYear());
    setMonth(base.getMonth() + 1);
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div>
        <h1 className="text-3xl font-bold">정산 내역</h1>
        <p className="mt-1 text-sm text-wb-secondary">공동구매별 매출과 수수료, 지급 예정액을 확인하세요.</p>
      </div>

      <div className="rounded-2xl border border-wb-line bg-wb-surface p-6">
        <h2 className="text-lg font-bold">매출 추이</h2>
        <p className="mt-1 text-xs text-wb-secondary">이번 달을 포함한 최근 12개월 매출이에요. 정산 확정 여부와 무관해요.</p>
        <div className="mt-4">
          {trendError ? (
            <Banner tone="error">{trendError}</Banner>
          ) : trendLoading ? (
            <p className="py-8 text-center text-sm text-wb-secondary">불러오는 중...</p>
          ) : trend.length === 0 ? (
            <p className="py-8 text-center text-sm text-wb-secondary">아직 매출 내역이 없어요.</p>
          ) : (
            <SettlementTrendChart data={trend} />
          )}
        </div>
      </div>

      {summaryError ? (
        <Banner tone="error">{summaryError}</Banner>
      ) : summaryLoading ? (
        <p className="py-4 text-center text-sm text-wb-secondary">이번 달 요약을 불러오는 중...</p>
      ) : summary ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <MetricCard
            icon={TrendingUp}
            title="이번 달 매출"
            value={won(summary.thisMonthTotalSales)}
            detail={changeRate(summary.thisMonthTotalSales, summary.previousMonthTotalSales)}
          />
          <MetricCard
            icon={Clock}
            title="정산 대기 중"
            value={won(summary.pendingAmount)}
            detail={summary.pendingItemCount > 0 ? `${summary.pendingItemCount}명` : undefined}
          />
          <MetricCard
            icon={CheckCircle2}
            title="이번 달 정산 완료"
            value={won(summary.thisMonthSettledAmount)}
            detail={summary.thisMonthSettledItemCount > 0 ? `${summary.thisMonthSettledItemCount}명` : undefined}
          />
        </div>
      ) : null}

      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-1">
          <button
            onClick={() => shiftMonth(-1)}
            className="flex h-9 w-9 items-center justify-center rounded-full text-wb-secondary hover:bg-wb-canvas"
            aria-label="이전 달"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <p className="w-28 text-center text-base font-bold">
            {year}년 {month}월
          </p>
          <button
            onClick={() => shiftMonth(1)}
            disabled={isCurrentMonth}
            className="flex h-9 w-9 items-center justify-center rounded-full text-wb-secondary hover:bg-wb-canvas disabled:opacity-30"
            aria-label="다음 달"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>

        <div className="flex gap-1.5 rounded-full bg-wb-canvas p-1">
          {STATUS_TABS.map((tab) => (
            <button
              key={tab.value}
              onClick={() => setStatusFilter(tab.value)}
              className={`rounded-full px-3.5 py-1.5 text-xs font-bold transition-colors ${
                statusFilter === tab.value ? "bg-wb-green text-white" : "text-wb-secondary hover:text-wb-ink"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : error && settlements.length === 0 ? (
        <Banner tone="error">{error}</Banner>
      ) : settlements.length === 0 ? (
        <EmptyState
          icon={Wallet}
          title="정산 내역이 없어요"
          message={`${year}년 ${month}월에는 해당하는 정산 내역이 없어요.`}
        />
      ) : (
        <div className="space-y-4">
          {settlements.map((settlement) => (
            <button
              key={`${settlement.status}-${settlement.groupBuyId}`}
              onClick={() => setSelected(settlement)}
              className="w-full space-y-4 rounded-2xl border border-wb-line bg-wb-surface p-5 text-left transition-colors hover:bg-wb-canvas/50"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <p className="line-clamp-1 text-lg font-bold">
                    {settlement.groupBuyTitle ?? `공동구매 #${settlement.groupBuyId}`}
                  </p>
                  <p className="text-xs text-wb-secondary">참여 {settlement.itemCount}명</p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <StatusPill tone={SETTLEMENT_LIST_STATUS_TONE[settlement.status]}>
                    {SETTLEMENT_LIST_STATUS_LABEL[settlement.status]}
                  </StatusPill>
                  <ChevronRight className="h-4 w-4 text-wb-secondary" />
                </div>
              </div>
              <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-3">
                <SettlementValue title="총 매출" value={settlement.totalSales} />
                <SettlementValue title={settlement.status === "PENDING" ? "예상 수수료" : "플랫폼 수수료"} value={settlement.platformFee} />
                <SettlementValue
                  title={settlement.status === "PENDING" ? "예상 지급액" : "지급액"}
                  value={settlement.payout}
                  highlighted
                />
              </div>
              <p className="text-xs text-wb-secondary">
                {settlement.status === "PENDING"
                  ? `공동구매 성사 · ${settlement.finalizedAt ? formatDateTime(settlement.finalizedAt) : "-"}`
                  : `정산 확정 · ${settlement.confirmedAt ? formatDateTime(settlement.confirmedAt) : "-"}`}
              </p>
            </button>
          ))}
        </div>
      )}

      {error && settlements.length > 0 && <Banner tone="error">{error}</Banner>}

      {hasNext && (
        <Button variant="secondary" className="w-full" loading={loadingMore} onClick={() => void loadMore()}>
          더 보기
        </Button>
      )}

      <SettlementDetailModal settlement={selected} open={selected !== null} onClose={() => setSelected(null)} />
    </div>
  );
}

function SettlementValue({
  title,
  value,
  highlighted = false,
}: {
  title: string;
  value: number;
  highlighted?: boolean;
}) {
  return (
    <div className={`rounded-lg p-3.5 ${highlighted ? "bg-wb-light-green/50" : "bg-wb-canvas"}`}>
      <p className="text-xs text-wb-secondary">{title}</p>
      <p className={`text-base font-bold ${highlighted ? "text-wb-green" : ""}`}>{won(value)}</p>
    </div>
  );
}
