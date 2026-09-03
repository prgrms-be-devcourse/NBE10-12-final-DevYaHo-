"use client";

import { useState } from "react";
import { Check, CreditCard, Landmark, Lock, Smartphone } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { PAYMENT_METHOD_LABEL, won, type PaymentMethod, type Participation } from "@/lib/mock/types";

const METHOD_ICONS: Record<PaymentMethod, typeof CreditCard> = {
  card: CreditCard,
  kakaoPay: Smartphone,
  bank: Landmark,
};

export function CheckoutModal({
  participation,
  open,
  onClose,
}: {
  participation: Participation;
  open: boolean;
  onClose: () => void;
}) {
  const { dealById, approvePayment } = useDemoStore();
  const [method, setMethod] = useState<PaymentMethod>("card");
  const [result, setResult] = useState<"success" | "failure" | null>(null);
  const [processing, setProcessing] = useState(false);

  const deal = dealById(participation.dealId)!;
  const total = (participation.finalPrice ?? participation.reservedPrice) * participation.quantity;

  function handleClose() {
    onClose();
    setTimeout(() => setResult(null), 200);
  }

  return (
    <Modal open={open} onClose={handleClose} title="최종가 결제" subtitle="공동구매가 마감되어 최종 가격이 확정됐어요.">
      {result === "success" ? (
        <ResultView
          tone="success"
          title="결제가 완료됐어요"
          message="주문이 생성되었습니다. 참여 내역에서 배송 상태를 확인할 수 있어요."
          buttonLabel="주문 확인"
          onAction={handleClose}
        />
      ) : result === "failure" ? (
        <ResultView
          tone="failure"
          title="결제를 완료하지 못했어요"
          message="승인 과정에서 오류가 발생했습니다. 결제수단을 확인하고 다시 시도해주세요."
          buttonLabel="다시 시도"
          onAction={() => setResult(null)}
        />
      ) : (
        <div className="space-y-5">
          <div className="flex items-center gap-3.5 rounded-xl bg-wb-canvas p-3.5">
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-bold">{deal.title}</p>
              <p className="text-xs text-wb-secondary">
                {participation.quantity}개 · 단가 {won(participation.finalPrice ?? participation.reservedPrice)}
              </p>
            </div>
            <p className="shrink-0 text-lg font-bold">{won(total)}</p>
          </div>

          <p className="text-sm font-bold">결제수단</p>
          <div className="space-y-2">
            {(Object.keys(PAYMENT_METHOD_LABEL) as PaymentMethod[]).map((m) => {
              const Icon = METHOD_ICONS[m];
              const active = method === m;
              return (
                <button
                  key={m}
                  onClick={() => setMethod(m)}
                  className={`flex w-full items-center gap-3 rounded-xl border px-4 py-3 text-sm font-semibold ${
                    active ? "border-wb-green/40 bg-wb-light-green/40" : "border-wb-line bg-wb-canvas"
                  }`}
                >
                  <Icon className="h-4 w-4 text-wb-green" />
                  {PAYMENT_METHOD_LABEL[m]}
                  {active && <Check className="ml-auto h-4 w-4 text-wb-green" />}
                </button>
              );
            })}
          </div>

          <Button
            className="w-full"
            loading={processing}
            onClick={() => {
              setProcessing(true);
              const success = approvePayment(participation.id, method);
              setProcessing(false);
              setResult(success ? "success" : "failure");
            }}
          >
            <Lock className="h-4 w-4" /> {won(total)} 결제
          </Button>
        </div>
      )}
    </Modal>
  );
}

function ResultView({
  tone,
  title,
  message,
  buttonLabel,
  onAction,
}: {
  tone: "success" | "failure";
  title: string;
  message: string;
  buttonLabel: string;
  onAction: () => void;
}) {
  return (
    <div className="flex flex-col items-center gap-4 py-6 text-center">
      <div
        className={`flex h-16 w-16 items-center justify-center rounded-full ${
          tone === "success" ? "bg-wb-light-green/70" : "bg-red-500/10"
        }`}
      >
        <Check className={`h-8 w-8 ${tone === "success" ? "text-wb-green" : "text-red-500"}`} strokeWidth={2.5} />
      </div>
      <h3 className="text-xl font-bold">{title}</h3>
      <p className="max-w-xs text-sm text-wb-secondary">{message}</p>
      <Button onClick={onAction} className="w-48">
        {buttonLabel}
      </Button>
    </div>
  );
}
