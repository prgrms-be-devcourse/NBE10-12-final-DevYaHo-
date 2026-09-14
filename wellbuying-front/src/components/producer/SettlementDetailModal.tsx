"use client";

import { useEffect, useState } from "react";
import { Banner } from "@/components/ui/Banner";
import { Modal } from "@/components/ui/Modal";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { getSettlementParticipants, getSettlementProgress } from "@/lib/api/settlement";
import { ApiError } from "@/lib/api/http";
import type {
  SettlementListItemResponse,
  SettlementParticipantResponse,
  SettlementProgressResponse,
} from "@/lib/api/types";
import { formatDateTime, won } from "@/lib/format";

// 목록 카드의 "상세 내용 보기" - PENDING이면 결제 진행도, COMPLETED면 결제한 참여자 명단을 보여준다
// (05-monthly-settlement-list.md / 06-settlement-detail.md 참고)
export function SettlementDetailModal({
  settlement,
  open,
  onClose,
}: {
  settlement: SettlementListItemResponse | null;
  open: boolean;
  onClose: () => void;
}) {
  const [progress, setProgress] = useState<SettlementProgressResponse | null>(null);
  const [participants, setParticipants] = useState<SettlementParticipantResponse[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open || !settlement) return;
    const { groupBuyId, status } = settlement;
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError(null);
      setProgress(null);
      setParticipants(null);
      try {
        if (status === "PENDING") {
          const res = await getSettlementProgress(groupBuyId);
          if (!cancelled) setProgress(res);
        } else {
          const res = await getSettlementParticipants(groupBuyId);
          if (!cancelled) setParticipants(res);
        }
      } catch (e) {
        if (!cancelled) setError(e instanceof ApiError ? e.message : "상세 내역을 불러오지 못했어요.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [open, settlement]);

  if (!settlement) return null;

  const ratio = progress && progress.totalParticipants > 0 ? progress.paidParticipants / progress.totalParticipants : 0;

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={settlement.groupBuyTitle ?? `공동구매 #${settlement.groupBuyId}`}
      subtitle={settlement.status === "PENDING" ? "정산 대기중 · 결제 진행도" : "정산 완료 · 결제한 참여자"}
    >
      {loading && <p className="py-6 text-center text-sm text-wb-secondary">불러오는 중...</p>}
      {error && <Banner tone="error">{error}</Banner>}

      {!loading && !error && progress && (
        <div className="space-y-3">
          <div className="flex items-baseline justify-between">
            <p className="text-sm font-semibold text-wb-secondary">결제 완료</p>
            <p className="text-lg font-bold">
              {progress.paidParticipants}
              <span className="text-sm font-semibold text-wb-secondary">명 / {progress.totalParticipants}명</span>
            </p>
          </div>
          <ProgressBar value={ratio} />
          <p className="text-xs text-wb-secondary">
            확정 참여자 중 아직 결제하지 않은 인원이 있으면, 재결제 유예기간이 끝나는 대로 정산이 확정돼요.
          </p>
        </div>
      )}

      {!loading && !error && participants && (
        participants.length === 0 ? (
          <p className="py-6 text-center text-sm text-wb-secondary">결제한 참여자가 없어요.</p>
        ) : (
          <div className="divide-y divide-wb-line">
            {participants.map((p) => (
              <div key={p.memberId} className="flex items-center justify-between gap-3 py-3 first:pt-0 last:pb-0">
                <div className="min-w-0">
                  <p className="truncate text-sm font-bold">{p.memberName ?? `회원 #${p.memberId}`}</p>
                  <p className="text-xs text-wb-secondary">{formatDateTime(p.paidAt)}</p>
                </div>
                <p className="shrink-0 text-sm font-bold">{won(p.amount)}</p>
              </div>
            ))}
          </div>
        )
      )}
    </Modal>
  );
}
