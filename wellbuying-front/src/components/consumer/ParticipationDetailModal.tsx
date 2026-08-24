"use client";

import { useState } from "react";
import { Bell, CreditCard, Flag, Package, UserPlus } from "lucide-react";
import { CheckoutModal } from "@/components/consumer/CheckoutModal";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Modal } from "@/components/ui/Modal";
import { StatusPill } from "@/components/ui/Tag";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { PARTICIPATION_STATE_LABEL, won, type ParticipationState } from "@/lib/mock/types";

const STEPS = [
  { icon: UserPlus, label: "참여" },
  { icon: Flag, label: "최종가 확정" },
  { icon: CreditCard, label: "결제" },
  { icon: Package, label: "배송" },
];

function stepIndex(state: ParticipationState) {
  switch (state) {
    case "recruiting":
      return 0;
    case "paymentRequired":
      return 1;
    case "paid":
      return 2;
    default:
      return 0;
  }
}

export function ParticipationDetailModal({
  participationId,
  open,
  onClose,
}: {
  participationId: string | null;
  open: boolean;
  onClose: () => void;
}) {
  const { participations, dealById, cancelParticipation } = useDemoStore();
  const [showCancel, setShowCancel] = useState(false);
  const [showCheckout, setShowCheckout] = useState(false);

  const participation = participationId
    ? participations.find((item) => item.id === participationId)
    : null;
  if (!participation) return null;
  const deal = dealById(participation.dealId)!;
  const total = (participation.finalPrice ?? participation.reservedPrice) * participation.quantity;
  const active = stepIndex(participation.state);

  return (
    <>
      <Modal open={open} onClose={onClose} title="참여 상세" subtitle={`참여번호 PT-${participation.id.slice(0, 8)}`}>
        <div className="space-y-5">
          <div className="flex items-center gap-3.5 rounded-xl bg-wb-canvas p-3.5">
            <div className="min-w-0 flex-1">
              <StatusPill tone={participation.state === "paymentRequired" ? "orange" : "green"}>
                {PARTICIPATION_STATE_LABEL[participation.state]}
              </StatusPill>
              <p className="mt-1.5 truncate text-sm font-bold">{deal.title}</p>
              <p className="text-xs text-wb-secondary">{deal.producer}</p>
            </div>
          </div>

          <div className="space-y-2.5 rounded-xl bg-wb-canvas p-4 text-sm">
            <div className="flex justify-between">
              <span className="text-wb-secondary">참여 수량</span>
              <span className="font-bold">{participation.quantity}개</span>
            </div>
            <div className="flex justify-between">
              <span className="text-wb-secondary">예약 단가</span>
              <span className="font-bold">{won(participation.reservedPrice)}</span>
            </div>
            {participation.finalPrice !== null && (
              <div className="flex justify-between">
                <span className="text-wb-secondary">확정 단가</span>
                <span className="font-bold text-wb-green">{won(participation.finalPrice)}</span>
              </div>
            )}
            <div className="flex justify-between border-t border-wb-line pt-2.5 text-base font-bold">
              <span>{participation.finalPrice === null ? "현재 예약 금액" : "최종 결제 금액"}</span>
              <span>{won(total)}</span>
            </div>
          </div>

          <div className="flex items-center rounded-xl bg-wb-canvas p-4">
            {STEPS.map((step, index) => {
              const Icon = step.icon;
              const reached = index <= active;
              return (
                <div key={step.label} className="flex flex-1 flex-col items-center gap-1.5">
                  <div
                    className={`flex h-8 w-8 items-center justify-center rounded-full ${
                      reached ? "bg-wb-light-green text-wb-green" : "bg-wb-surface text-wb-secondary"
                    }`}
                  >
                    <Icon className="h-3.5 w-3.5" />
                  </div>
                  <span className={`text-[10px] font-semibold ${reached ? "text-wb-green" : "text-wb-secondary"}`}>
                    {step.label}
                  </span>
                </div>
              );
            })}
          </div>

          {participation.state === "recruiting" && (
            <>
              <div className="flex items-start gap-2.5 rounded-xl bg-wb-light-green/40 p-3.5 text-xs text-wb-secondary">
                <Bell className="h-4 w-4 shrink-0 text-wb-green" />
                모집이 종료되면 확정된 최종 가격과 결제 시점을 알려드릴게요.
              </div>
              <Button variant="secondary" className="w-full text-red-600" onClick={() => setShowCancel(true)}>
                참여 취소
              </Button>
            </>
          )}

          {participation.state === "paymentRequired" && (
            <Button className="w-full" onClick={() => setShowCheckout(true)}>
              <CreditCard className="h-4 w-4" /> {won(total)} 결제하기
            </Button>
          )}

          {participation.state === "paid" && (
            <p className="rounded-xl bg-wb-light-green/50 p-3.5 text-sm font-semibold text-wb-green">
              결제가 완료되어 주문이 생성됐어요.
            </p>
          )}
        </div>
      </Modal>

      <ConfirmDialog
        open={showCancel}
        onClose={() => setShowCancel(false)}
        onConfirm={() => {
          cancelParticipation(participation.id);
          onClose();
        }}
        title="참여를 취소할까요?"
        message="모집 중인 공동구매만 참여를 취소할 수 있습니다."
        confirmLabel="참여 취소"
        destructive
      />

      <CheckoutModal
        participation={participation}
        open={showCheckout}
        onClose={() => {
          setShowCheckout(false);
          onClose();
        }}
      />
    </>
  );
}
