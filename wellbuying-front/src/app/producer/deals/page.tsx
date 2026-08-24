"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { DealArtwork } from "@/components/deal/DealArtwork";
import { DealEditorModal } from "@/components/producer/DealEditorModal";
import { ProducerStatusPill } from "@/components/producer/ProducerStatusPill";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { Tag } from "@/components/ui/Tag";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { activeTier, won, type Deal } from "@/lib/mock/types";

export default function ProducerDealsPage() {
  const { deals, producerDealIds, participants, dealStatuses, setDealStatus, completeDeal, canCancelBeforeStart } =
    useDemoStore();
  const [showCreate, setShowCreate] = useState(false);
  const [editingDeal, setEditingDeal] = useState<Deal | null>(null);
  const [cancelDealId, setCancelDealId] = useState<string | null>(null);
  const [completeDealId, setCompleteDealId] = useState<string | null>(null);

  const producerDeals = deals.filter((deal) => producerDealIds.has(deal.id));

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">공동구매 관리</h1>
          <p className="mt-1 text-sm text-wb-secondary">임시저장부터 모집 종료까지 상태를 직접 확인할 수 있어요.</p>
        </div>
        <Button onClick={() => setShowCreate(true)}>
          <Plus className="h-4 w-4" /> 공동구매 개설
        </Button>
      </div>

      <div className="space-y-4">
        {producerDeals.map((deal) => {
          const status = dealStatuses[deal.id] ?? "draft";
          const people = participants(deal.id);
          return (
            <div key={deal.id} className="space-y-4 rounded-2xl border border-wb-line bg-wb-surface p-5">
              <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div className="flex gap-4">
                  <DealArtwork deal={deal} className="h-24 w-32 shrink-0" />
                  <div>
                    <div className="mb-1.5 flex gap-2">
                      <ProducerStatusPill status={status} />
                      <Tag>{deal.category}</Tag>
                    </div>
                    <p className="line-clamp-2 text-lg font-bold">{deal.title}</p>
                    <p className="mt-1 text-xs text-wb-secondary">
                      현재 {people.toLocaleString("ko-KR")}명 · 목표 {deal.targetPeople.toLocaleString("ko-KR")}명 ·
                      D-{deal.daysLeft}
                    </p>
                  </div>
                </div>
                <p className="text-lg font-bold sm:text-right">{won(activeTier(deal, people).price)}</p>
              </div>

              <ProgressBar value={people / deal.targetPeople} />

              <div className="flex flex-wrap items-center gap-2">
                <Button
                  variant="secondary"
                  disabled={status === "completed" || status === "cancelled"}
                  onClick={() => setEditingDeal(deal)}
                >
                  수정
                </Button>
                {(status === "draft" || status === "scheduled" || status === "paused") && (
                  <Button variant="secondary" onClick={() => setDealStatus(deal.id, "recruiting")}>
                    {status === "paused" ? "모집 재개" : "지금 공개"}
                  </Button>
                )}
                {status === "recruiting" && (
                  <>
                    <Button variant="secondary" onClick={() => setDealStatus(deal.id, "paused")}>
                      일시 중지
                    </Button>
                    <Button variant="secondary" onClick={() => setCompleteDealId(deal.id)}>
                      모집 종료
                    </Button>
                  </>
                )}
                {canCancelBeforeStart(deal.id) && (
                  <button
                    onClick={() => setCancelDealId(deal.id)}
                    className="ml-auto text-sm font-semibold text-red-600"
                  >
                    공동구매 취소
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>

      <DealEditorModal open={showCreate} onClose={() => setShowCreate(false)} />
      <DealEditorModal
        open={editingDeal !== null}
        editing={editingDeal}
        onClose={() => setEditingDeal(null)}
      />

      <ConfirmDialog
        open={cancelDealId !== null}
        onClose={() => setCancelDealId(null)}
        onConfirm={() => cancelDealId && setDealStatus(cancelDealId, "cancelled")}
        title="공동구매를 취소할까요?"
        message="공개 전 공동구매가 삭제되고 소비자 목록에는 표시되지 않습니다."
        confirmLabel="공동구매 취소"
        destructive
      />
      <ConfirmDialog
        open={completeDealId !== null}
        onClose={() => setCompleteDealId(null)}
        onConfirm={() => completeDealId && completeDeal(completeDealId)}
        title="모집을 종료할까요?"
        message="현재 참여 인원을 기준으로 최종 가격을 확정하고 참여자에게 결제를 요청합니다."
        confirmLabel="모집 종료"
      />
    </div>
  );
}
