"use client";

import { useState } from "react";
import { PlayCircle } from "lucide-react";
import { Banner } from "@/components/ui/Banner";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { StatusPill } from "@/components/ui/Tag";
import { compactWon, won } from "@/lib/format";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { SETTLEMENT_STATUS_LABEL, type SettlementStatus } from "@/lib/mock/types";

const TONE: Record<SettlementStatus, "orange" | "green" | "red"> = {
  ready: "orange",
  completed: "green",
  held: "red",
};

export default function AdminSettlementsPage() {
  const { settlements, settlementStatuses, readySettlementCount, readySettlementAmount, lastBatchRun, runSettlementBatch } =
    useDemoStore();
  const [showConfirm, setShowConfirm] = useState(false);
  const [showSuccess, setShowSuccess] = useState(false);

  const lastRunText = lastBatchRun
    ? new Date(lastBatchRun).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })
    : "실행 기록 없음";

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div>
        <p className="text-xs font-bold tracking-wide text-wb-green">SETTLEMENT</p>
        <h1 className="mt-1 text-3xl font-bold">정산 관리</h1>
        <p className="mt-1 text-sm text-wb-secondary">검증이 끝난 정산 내역을 확인하고 배치를 실행합니다.</p>
      </div>

      {showSuccess && (
        <Banner tone="success">정산 배치를 완료했습니다. 중복 실행 방지를 위한 멱등성 키가 기록됐어요.</Banner>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="rounded-2xl border border-wb-line bg-wb-surface p-5">
          <p className="text-xs font-semibold text-wb-secondary">이번 정산 대기 금액</p>
          <p className="mt-2 text-2xl font-bold">{compactWon(readySettlementAmount)}</p>
          <p className="mt-1 text-xs font-semibold text-wb-green">검증 완료 {readySettlementCount}건</p>
        </div>
        <div className="rounded-2xl border border-wb-line bg-wb-surface p-5">
          <p className="text-xs font-semibold text-wb-secondary">최근 배치 실행</p>
          <p className="mt-2 text-2xl font-bold">{lastRunText}</p>
        </div>
        <button
          onClick={() => setShowConfirm(true)}
          disabled={readySettlementCount === 0}
          className="flex flex-col items-center justify-center gap-1.5 rounded-2xl bg-wb-green py-5 text-white disabled:opacity-40"
        >
          <PlayCircle className="h-6 w-6" />
          <span className="text-sm font-bold">정산 배치 실행</span>
          <span className="text-xs text-white/70">대기 {readySettlementCount}건</span>
        </button>
      </div>

      <div className="space-y-3">
        {settlements.map((record) => {
          const status = settlementStatuses[record.id] ?? "ready";
          return (
            <div key={record.id} className="rounded-2xl border border-wb-line bg-wb-surface p-4">
              <div className="mb-3 flex items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-bold">{record.producer}</p>
                  <p className="text-xs text-wb-secondary">{record.groupBuyTitle}</p>
                </div>
                <StatusPill tone={TONE[status]}>{SETTLEMENT_STATUS_LABEL[status]}</StatusPill>
              </div>
              <div className="grid grid-cols-3 gap-2.5 text-xs">
                <div>
                  <p className="text-wb-secondary">총 매출</p>
                  <p className="font-bold">{won(record.sales)}</p>
                </div>
                <div>
                  <p className="text-wb-secondary">수수료</p>
                  <p className="font-bold">{won(record.platformFee)}</p>
                </div>
                <div>
                  <p className="text-wb-secondary">지급 예정</p>
                  <p className="font-bold text-wb-green">{won(record.payout)}</p>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <ConfirmDialog
        open={showConfirm}
        onClose={() => setShowConfirm(false)}
        onConfirm={() => {
          runSettlementBatch();
          setShowSuccess(true);
        }}
        title="정산 배치를 실행할까요?"
        message={`정산 대기 ${readySettlementCount}건, 총 ${won(readySettlementAmount)}이 처리됩니다.`}
        confirmLabel="실행"
      />
    </div>
  );
}
