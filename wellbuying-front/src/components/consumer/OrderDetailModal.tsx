"use client";

import { useState } from "react";
import { CheckCircle2, Home, PackageCheck, Truck } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Modal } from "@/components/ui/Modal";
import { StatusPill } from "@/components/ui/Tag";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import {
  DELIVERY_STATES,
  DELIVERY_STATE_LABEL,
  PAYMENT_METHOD_LABEL,
  PAYMENT_STATE_LABEL,
  won,
} from "@/lib/mock/types";

const DELIVERY_ICONS = {
  preparing: PackageCheck,
  shipping: Truck,
  delivered: Home,
  confirmed: CheckCircle2,
} as const;

export function OrderDetailModal({
  orderId,
  open,
  onClose,
}: {
  orderId: string | null;
  open: boolean;
  onClose: () => void;
}) {
  const { orders, dealById, confirmPurchase, cancelOrRefund, advanceDelivery } = useDemoStore();
  const [showCancel, setShowCancel] = useState(false);

  const order = orderId ? orders.find((item) => item.id === orderId) : null;
  if (!order) return null;
  const deal = dealById(order.dealId)!;
  const reachedIndex = DELIVERY_STATES.indexOf(order.deliveryState);
  const total = order.unitPrice * order.quantity;
  const isPreparing = order.deliveryState === "preparing";

  return (
    <>
      <Modal open={open} onClose={onClose} title="주문 상세" subtitle={order.orderNumber} width="600px">
        <div className="space-y-5">
          <div className="flex items-center gap-3.5 rounded-xl bg-wb-canvas p-3.5">
            <div className="min-w-0 flex-1">
              <StatusPill tone={order.paymentState === "approved" ? "green" : "orange"}>
                {order.paymentState === "approved" ? DELIVERY_STATE_LABEL[order.deliveryState] : PAYMENT_STATE_LABEL[order.paymentState]}
              </StatusPill>
              <p className="mt-1.5 truncate text-sm font-bold">{deal.title}</p>
              <p className="text-xs text-wb-secondary">
                {order.quantity}개 · {PAYMENT_METHOD_LABEL[order.paymentMethod]}
              </p>
            </div>
          </div>

          {order.paymentState === "approved" && (
            <div className="rounded-xl bg-wb-canvas p-4">
              <p className="mb-4 text-sm font-bold">배송 현황</p>
              <div className="flex items-center">
                {DELIVERY_STATES.map((state, index) => {
                  const Icon = DELIVERY_ICONS[state];
                  const reached = index <= reachedIndex;
                  return (
                    <div key={state} className="flex flex-1 flex-col items-center gap-1.5">
                      <div
                        className={`flex h-8 w-8 items-center justify-center rounded-full ${
                          reached ? "bg-wb-light-green text-wb-green" : "bg-wb-surface text-wb-secondary"
                        }`}
                      >
                        <Icon className="h-3.5 w-3.5" />
                      </div>
                      <span
                        className={`text-center text-[10px] font-semibold ${reached ? "text-wb-green" : "text-wb-secondary"}`}
                      >
                        {DELIVERY_STATE_LABEL[state]}
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          <div className="space-y-2.5 rounded-xl bg-wb-canvas p-4 text-sm">
            <div className="flex justify-between">
              <span className="text-wb-secondary">상품 금액</span>
              <span className="font-bold">{won(total)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-wb-secondary">배송비</span>
              <span className="font-bold">무료</span>
            </div>
            <div className="flex justify-between border-t border-wb-line pt-2.5 text-base font-bold">
              <span>총 결제 금액</span>
              <span>{won(total)}</span>
            </div>
          </div>

          {order.paymentState === "approved" ? (
            order.deliveryState === "delivered" ? (
              <div className="space-y-2">
                <Button className="w-full" onClick={() => confirmPurchase(order.id)}>
                  구매 확정
                </Button>
                <Button variant="secondary" className="w-full text-red-600" onClick={() => setShowCancel(true)}>
                  환불 요청
                </Button>
              </div>
            ) : order.deliveryState === "confirmed" ? (
              <p className="rounded-xl bg-wb-light-green/50 p-3.5 text-sm font-semibold text-wb-green">
                구매가 확정되었습니다.
              </p>
            ) : (
              <div className="space-y-2">
                <p className="rounded-xl bg-wb-light-green/40 p-3.5 text-xs text-wb-secondary">
                  {isPreparing
                    ? "생산자가 상품을 준비하고 있어요."
                    : "상품이 배송 중이에요. 배송 완료 후 구매를 확정할 수 있어요."}
                </p>
                <Button variant="secondary" className="w-full" onClick={() => advanceDelivery(order.id)}>
                  {isPreparing ? "배송 시작 (데모)" : "배송 완료 처리 (데모)"}
                </Button>
                <Button variant="secondary" className="w-full text-red-600" onClick={() => setShowCancel(true)}>
                  {isPreparing ? "주문·결제 취소" : "환불 요청"}
                </Button>
              </div>
            )
          ) : (
            <p className="rounded-xl bg-wb-orange/10 p-3.5 text-sm font-semibold text-wb-orange">
              {PAYMENT_STATE_LABEL[order.paymentState]}
            </p>
          )}
        </div>
      </Modal>

      <ConfirmDialog
        open={showCancel}
        onClose={() => setShowCancel(false)}
        onConfirm={() => cancelOrRefund(order.id)}
        title={isPreparing ? "주문을 취소할까요?" : "환불을 요청할까요?"}
        message="처리 후에는 되돌릴 수 없어요."
        confirmLabel={isPreparing ? "주문 취소" : "환불 요청"}
        destructive
      />
    </>
  );
}
