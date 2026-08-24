"use client";

import { useState } from "react";
import { Check, Minus, Plus } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { activeTier, won, type Deal } from "@/lib/mock/types";

export function ParticipationModal({
  deal,
  open,
  onClose,
}: {
  deal: Deal;
  open: boolean;
  onClose: () => void;
}) {
  const { participants, participate } = useDemoStore();
  const [quantity, setQuantity] = useState(1);
  const [completed, setCompleted] = useState(false);

  const peopleAfter = participants(deal.id) + quantity;
  const tier = activeTier(deal, peopleAfter);

  function handleClose() {
    onClose();
    setTimeout(() => {
      setQuantity(1);
      setCompleted(false);
    }, 200);
  }

  return (
    <Modal open={open} onClose={handleClose} title={completed ? "참여 완료" : "공동구매 참여"} subtitle={completed ? undefined : deal.title}>
      {completed ? (
        <div className="flex flex-col items-center gap-4 py-6 text-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-wb-light-green/70">
            <Check className="h-8 w-8 text-wb-green" strokeWidth={2.5} />
          </div>
          <h3 className="text-xl font-bold">참여가 완료됐어요</h3>
          <p className="text-sm text-wb-secondary">
            종료 시점에 달성된 가장 낮은 가격으로
            <br />
            최종 결제됩니다.
          </p>
          <Button onClick={handleClose} className="w-48">
            확인
          </Button>
        </div>
      ) : (
        <div className="space-y-5">
          <div className="flex items-center justify-between">
            <p className="text-sm font-bold">구매할 수량을 선택해주세요</p>
            <div className="flex items-center gap-4 rounded-full bg-wb-canvas px-3.5 py-1.5">
              <button
                onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                disabled={quantity === 1}
                className="flex h-7 w-7 items-center justify-center rounded-full disabled:opacity-30"
              >
                <Minus className="h-3.5 w-3.5" />
              </button>
              <span className="w-5 text-center text-base font-bold">{quantity}</span>
              <button
                onClick={() => setQuantity((q) => Math.min(10, q + 1))}
                disabled={quantity === 10}
                className="flex h-7 w-7 items-center justify-center rounded-full disabled:opacity-30"
              >
                <Plus className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>

          <div className="space-y-2.5 rounded-xl bg-wb-canvas p-4 text-sm">
            <div className="flex justify-between">
              <span className="text-wb-secondary">현재 예약 단가</span>
              <span className="font-bold">{won(tier.price)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-wb-secondary">수량</span>
              <span className="font-bold">{quantity}개</span>
            </div>
            <div className="flex justify-between border-t border-wb-line pt-2.5 text-base">
              <span className="font-bold">현재 예약 금액</span>
              <span className="font-bold text-wb-green">{won(tier.price * quantity)}</span>
            </div>
          </div>

          <p className="text-xs text-wb-secondary">
            참여자가 더 모여 다음 구간에 도달하면 별도 신청 없이 더 낮은 가격이 적용됩니다.
          </p>

          <Button
            className="w-full"
            onClick={() => {
              participate(deal.id, quantity);
              setCompleted(true);
            }}
          >
            {won(tier.price * quantity)}으로 참여하기
          </Button>
          <p className="text-center text-xs text-wb-secondary">
            지금 결제되지 않아요 · 공동구매 종료 후 최종가로 결제
          </p>
        </div>
      )}
    </Modal>
  );
}
