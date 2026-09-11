"use client";

import { useEffect, useState } from "react";
import { Wallet } from "lucide-react";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { StatusPill } from "@/components/ui/Tag";
import { ApiError } from "@/lib/api/http";
import { listMySettlements } from "@/lib/api/settlement";
import type { SettlementResponse } from "@/lib/api/types";
import { formatDateTime, won } from "@/lib/format";
import { SETTLEMENT_STATUS_LABEL, SETTLEMENT_STATUS_TONE } from "@/lib/settlement/settlementStatus";

const PAGE_SIZE = 10;

export default function ProducerSettlementsPage() {
  const [settlements, setSettlements] = useState<SettlementResponse[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function loadFirstPage() {
      setLoading(true);
      setError(null);
      try {
        const res = await listMySettlements({ page: 0, size: PAGE_SIZE });
        if (cancelled) return;
        setSettlements(res.content);
        setPage(res.page.number);
        setHasNext(res.page.number + 1 < res.page.totalPages);
      } catch (e) {
        if (!cancelled) setError(e instanceof ApiError ? e.message : "정산 내역을 불러오지 못했어요.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void loadFirstPage();
    return () => {
      cancelled = true;
    };
  }, []);

  async function loadMore() {
    setLoadingMore(true);
    setError(null);
    try {
      const res = await listMySettlements({ page: page + 1, size: PAGE_SIZE });
      setSettlements((prev) => [...prev, ...res.content]);
      setPage(res.page.number);
      setHasNext(res.page.number + 1 < res.page.totalPages);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "더 불러오지 못했어요.");
    } finally {
      setLoadingMore(false);
    }
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div>
        <h1 className="text-3xl font-bold">정산 내역</h1>
        <p className="mt-1 text-sm text-wb-secondary">공동구매별 매출과 수수료, 지급 예정액을 확인하세요.</p>
      </div>

      {loading ? (
        <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : error && settlements.length === 0 ? (
        <Banner tone="error">{error}</Banner>
      ) : settlements.length === 0 ? (
        <EmptyState
          icon={Wallet}
          title="아직 정산 내역이 없어요"
          message="공동구매 정산이 확정되면 여기에서 매출과 지급 예정액을 확인할 수 있어요."
        />
      ) : (
        <div className="space-y-4">
          {settlements.map((settlement) => (
            <div
              key={settlement.settlementId}
              className="space-y-4 rounded-2xl border border-wb-line bg-wb-surface p-5"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-lg font-bold">
                    {settlement.groupBuyTitle ?? `공동구매 #${settlement.groupBuyId}`}
                  </p>
                  <p className="text-xs text-wb-secondary">
                    정산번호 ST-{String(settlement.settlementId).padStart(6, "0")} · 참여 {settlement.itemCount}건
                  </p>
                </div>
                <StatusPill tone={SETTLEMENT_STATUS_TONE[settlement.status]}>
                  {SETTLEMENT_STATUS_LABEL[settlement.status]}
                </StatusPill>
              </div>
              <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-3">
                <SettlementValue title="총 매출" value={settlement.totalSales} />
                <SettlementValue title="플랫폼 수수료" value={settlement.platformFee} />
                <SettlementValue title="지급 예정" value={settlement.payout} highlighted />
              </div>
              <p className="text-xs text-wb-secondary">정산 확정 · {formatDateTime(settlement.confirmedAt)}</p>
            </div>
          ))}
        </div>
      )}

      {error && settlements.length > 0 && <Banner tone="error">{error}</Banner>}

      {hasNext && (
        <Button variant="secondary" className="w-full" loading={loadingMore} onClick={() => void loadMore()}>
          더 보기
        </Button>
      )}
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
