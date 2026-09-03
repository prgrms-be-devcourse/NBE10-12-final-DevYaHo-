"use client";

import { useState } from "react";
import { ChevronRight, ShoppingBag } from "lucide-react";
import { AccountShell } from "@/components/account/AccountShell";
import { DealArtwork } from "@/components/deal/DealArtwork";
import { OrderDetailModal } from "@/components/consumer/OrderDetailModal";
import { ParticipationDetailModal } from "@/components/consumer/ParticipationDetailModal";
import { EmptyState } from "@/components/ui/EmptyState";
import { StatusPill } from "@/components/ui/Tag";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import {
  DELIVERY_STATE_LABEL,
  PARTICIPATION_STATE_LABEL,
  PAYMENT_STATE_LABEL,
  won,
} from "@/lib/mock/types";

function OrdersContent() {
  const { participations, orders, dealById } = useDemoStore();
  const [selectedParticipationId, setSelectedParticipationId] = useState<string | null>(null);
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);

  if (participations.length === 0 && orders.length === 0) {
    return (
      <EmptyState
        icon={ShoppingBag}
        title="참여한 공동구매가 없어요"
        message="공동구매에 참여하면 최종 가격과 진행 상태를 여기서 볼 수 있어요."
      />
    );
  }

  return (
    <div className="space-y-6">
      {orders.length > 0 && (
        <section className="space-y-3">
          <h2 className="text-lg font-bold">결제·배송</h2>
          {orders.map((order) => {
            const deal = dealById(order.dealId)!;
            return (
              <button
                key={order.id}
                onClick={() => setSelectedOrderId(order.id)}
                className="flex w-full items-center gap-4 rounded-xl border border-wb-line bg-wb-surface p-4 text-left hover:shadow-sm"
              >
                <DealArtwork deal={deal} className="h-16 w-20 shrink-0" />
                <div className="min-w-0 flex-1 space-y-1.5">
                  <StatusPill tone={order.paymentState === "approved" ? "green" : "orange"}>
                    {order.paymentState === "approved" ? DELIVERY_STATE_LABEL[order.deliveryState] : PAYMENT_STATE_LABEL[order.paymentState]}
                  </StatusPill>
                  <p className="line-clamp-1 font-bold">{deal.title}</p>
                  <p className="text-xs text-wb-secondary">
                    {order.quantity}개 · {won(order.unitPrice * order.quantity)} · {order.orderNumber}
                  </p>
                </div>
                <ChevronRight className="h-4 w-4 shrink-0 text-wb-secondary" />
              </button>
            );
          })}
        </section>
      )}

      {participations.length > 0 && (
        <section className="space-y-3">
          <h2 className="text-lg font-bold">공동구매 참여</h2>
          {participations.map((item) => {
            const deal = dealById(item.dealId)!;
            const price = item.finalPrice ?? item.reservedPrice;
            return (
              <button
                key={item.id}
                onClick={() => setSelectedParticipationId(item.id)}
                className="flex w-full items-center gap-4 rounded-xl border border-wb-line bg-wb-surface p-4 text-left hover:shadow-sm"
              >
                <DealArtwork deal={deal} className="h-16 w-20 shrink-0" />
                <div className="min-w-0 flex-1 space-y-1.5">
                  <StatusPill tone={item.state === "paymentRequired" ? "orange" : "green"}>
                    {PARTICIPATION_STATE_LABEL[item.state]}
                  </StatusPill>
                  <p className="line-clamp-1 font-bold">{deal.title}</p>
                  <p className="text-xs text-wb-secondary">
                    {item.quantity}개 · {item.finalPrice === null ? "예약가" : "확정가"} {won(price * item.quantity)}
                  </p>
                </div>
                {item.state === "paymentRequired" && (
                  <span className="shrink-0 text-xs font-bold text-wb-orange">결제하기</span>
                )}
                <ChevronRight className="h-4 w-4 shrink-0 text-wb-secondary" />
              </button>
            );
          })}
        </section>
      )}

      <ParticipationDetailModal
        participationId={selectedParticipationId}
        open={selectedParticipationId !== null}
        onClose={() => setSelectedParticipationId(null)}
      />
      <OrderDetailModal
        orderId={selectedOrderId}
        open={selectedOrderId !== null}
        onClose={() => setSelectedOrderId(null)}
      />
    </div>
  );
}

export default function OrdersPage() {
  return (
    <AccountShell>
      <OrdersContent />
    </AccountShell>
  );
}
