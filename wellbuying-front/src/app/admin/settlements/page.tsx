"use client";

import { useEffect, useRef, useState } from "react";
import { CheckCircle2, Clock, Wallet } from "lucide-react";
import { Banner } from "@/components/ui/Banner";
import { EmptyState } from "@/components/ui/EmptyState";
import { MetricCard } from "@/components/ui/MetricCard";
import { Pagination } from "@/components/ui/Pagination";
import { StatusPill } from "@/components/ui/Tag";
import { getAdminSettlementSummary, listAdminSettlements } from "@/lib/api/admin";
import { ApiError } from "@/lib/api/http";
import type { AdminSettlementSummaryResponse, PageResponse, SettlementResponse, SettlementStatus } from "@/lib/api/types";
import { formatDateTime, won } from "@/lib/format";
import { usePagedQuery } from "@/hooks/usePagedQuery";

const STATUS_OPTIONS: { value: SettlementStatus | ""; label: string }[] = [
  { value: "", label: "전체 상태" },
  { value: "CONFIRMED", label: "정산 확정" },
  { value: "PAID", label: "지급 완료" },
];

const STATUS_LABEL: Record<SettlementStatus, string> = {
  CONFIRMED: "정산 확정",
  PAID: "지급 완료",
};

const STATUS_TONE: Record<SettlementStatus, "orange" | "green"> = {
  CONFIRMED: "orange",
  PAID: "green",
};

function SummaryCards() {
  const [summary, setSummary] = useState<AdminSettlementSummaryResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    getAdminSettlementSummary()
      .then((res) => {
        if (!ignore) setSummary(res);
      })
      .catch((e) => {
        if (!ignore) setError(e instanceof ApiError ? e.message : "요약 정보를 불러오지 못했어요.");
      });
    return () => {
      ignore = true;
    };
  }, []);

  if (error) return <Banner tone="error">{error}</Banner>;
  if (!summary) return <p className="py-4 text-center text-sm text-wb-secondary">요약 정보를 불러오는 중...</p>;

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <MetricCard
        icon={Clock}
        title="정산 대기 중"
        value={won(summary.pendingAmount)}
        detail={summary.pendingCount > 0 ? `${summary.pendingCount}건` : undefined}
      />
      <MetricCard
        icon={CheckCircle2}
        title="이번 달 정산 완료"
        value={won(summary.thisMonthConfirmedAmount)}
        detail={summary.thisMonthConfirmedCount > 0 ? `${summary.thisMonthConfirmedCount}건` : undefined}
      />
    </div>
  );
}

export default function AdminSettlementsPage() {
  const [status, setStatus] = useState<SettlementStatus | "">("");
  const [page, setPage] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  function handleKeywordChange(value: string) {
    setKeyword(value);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setDebouncedKeyword(value);
      setPage(0);
    }, 300);
  }

  useEffect(() => {
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, []);

  const { data, error, loading } = usePagedQuery<PageResponse<SettlementResponse>>(
    "admin-settlements",
    { status: status || undefined, keyword: debouncedKeyword, page },
    () => listAdminSettlements({ status: status || undefined, keyword: debouncedKeyword || undefined, page, size: 10 }),
    "정산 내역을 불러오지 못했어요.",
  );
  const items = data?.content ?? null;
  const totalPages = data?.page.totalPages ?? 0;

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div>
        <p className="text-xs font-bold tracking-wide text-wb-green">SETTLEMENT</p>
        <h1 className="mt-1 text-3xl font-bold">정산 관리</h1>
        <p className="mt-1 text-sm text-wb-secondary">공동구매별 확정 정산 내역을 확인합니다.</p>
      </div>

      <SummaryCards />

      <div className="flex flex-col gap-2 sm:flex-row">
        <input
          type="text"
          value={keyword}
          onChange={(e) => handleKeywordChange(e.target.value)}
          placeholder="공동구매 제목으로 검색"
          className="w-full rounded-lg border border-wb-line bg-white px-4 py-2.5 text-sm outline-none focus:border-wb-green sm:flex-1"
        />
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as SettlementStatus | "");
            setPage(0);
          }}
          className="rounded-lg border border-wb-line bg-white px-3 py-2.5 text-sm outline-none focus:border-wb-green sm:w-40"
        >
          {STATUS_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      </div>

      {error && <Banner tone="error">{error}</Banner>}

      {loading && items === null ? (
        <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : items === null || items.length === 0 ? (
        <EmptyState icon={Wallet} title="정산 내역이 없어요" message="조건에 해당하는 정산 내역이 없어요." />
      ) : (
        <>
          <div className="space-y-3">
            {items.map((item) => (
              <div key={item.settlementId} className="space-y-3 rounded-2xl border border-wb-line bg-wb-surface p-5">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="line-clamp-1 text-base font-bold">{item.groupBuyTitle ?? `공동구매 #${item.groupBuyId}`}</p>
                    <p className="text-xs text-wb-secondary">
                      {item.producerName ?? `생산자 #${item.producerId}`} · 참여 {item.itemCount}명
                    </p>
                  </div>
                  <StatusPill tone={STATUS_TONE[item.status]}>{STATUS_LABEL[item.status]}</StatusPill>
                </div>
                <div className="grid grid-cols-3 gap-2.5 text-xs">
                  <div>
                    <p className="text-wb-secondary">총 매출</p>
                    <p className="font-bold">{won(item.totalSales)}</p>
                  </div>
                  <div>
                    <p className="text-wb-secondary">플랫폼 수수료</p>
                    <p className="font-bold">{won(item.platformFee)}</p>
                  </div>
                  <div>
                    <p className="text-wb-secondary">지급액</p>
                    <p className="font-bold text-wb-green">{won(item.payout)}</p>
                  </div>
                </div>
                <p className="text-xs text-wb-secondary">확정일 · {formatDateTime(item.confirmedAt)}</p>
              </div>
            ))}
          </div>

          <Pagination page={page} totalPages={totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}
