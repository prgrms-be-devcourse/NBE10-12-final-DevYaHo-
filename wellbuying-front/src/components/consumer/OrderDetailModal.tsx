"use client";

import { useEffect, useState } from "react";
import { CheckCircle2, Home, PackageCheck, ShoppingBag, Truck, type LucideIcon } from "lucide-react";
import { OrderThumbnail } from "@/components/consumer/OrderThumbnail";
import { Banner } from "@/components/ui/Banner";
import { Modal } from "@/components/ui/Modal";
import { StatusPill } from "@/components/ui/Tag";
import { ApiError } from "@/lib/api/http";
import { getMyOrder } from "@/lib/api/orders";
import type { OrderDetailResponse, OrderStatus } from "@/lib/api/types";
import { formatDateTime, won } from "@/lib/format";
import { ORDER_STATUS_LABEL, ORDER_STATUS_TONE } from "@/lib/order/orderStatus";

// 승인이 끝난 주문의 진행 단계. 배송 상태(PREPARING~)는 shipping 도메인이 붙기 전까지는 PAID에 머무른다
const STEPS: { status: OrderStatus; label: string; icon: LucideIcon }[] = [
  { status: "PAID", label: "결제 완료", icon: ShoppingBag },
  { status: "PREPARING", label: "상품 준비", icon: PackageCheck },
  { status: "SHIPPING", label: "배송 중", icon: Truck },
  { status: "DELIVERED", label: "배송 완료", icon: Home },
  { status: "CONFIRMED", label: "구매 확정", icon: CheckCircle2 },
];

const PG_PROVIDER_LABEL: Record<string, string> = {
  TOSS: "토스페이먼츠",
};

export function OrderDetailModal({
  orderId,
  open,
  onClose,
}: {
  orderId: string | null;
  open: boolean;
  onClose: () => void;
}) {
  const [detail, setDetail] = useState<OrderDetailResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open || !orderId) return;
    const id = orderId;
    let cancelled = false;
    async function loadDetail() {
      setLoading(true);
      setError(null);
      setDetail(null);
      try {
        const res = await getMyOrder(id);
        if (!cancelled) setDetail(res);
      } catch (e) {
        if (!cancelled) setError(e instanceof ApiError ? e.message : "결제 정보를 불러오지 못했어요.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void loadDetail();
    return () => {
      cancelled = true;
    };
  }, [open, orderId]);

  const stepIndex = detail ? STEPS.findIndex((s) => s.status === detail.status) : -1;
  const showStepper = detail?.paymentStatus === "APPROVED" && stepIndex >= 0;
  const itemAmount = detail?.unitPrice != null ? detail.unitPrice * detail.quantity : detail?.totalPrice ?? 0;

  return (
    <Modal open={open} onClose={onClose} title="결제 상세" subtitle={orderId ?? undefined} width="600px">
      {loading && <p className="py-6 text-sm text-wb-secondary">불러오는 중...</p>}
      {error && <Banner tone="error">{error}</Banner>}

      {detail && (
        <div className="space-y-5">
          <div className="flex items-center gap-3.5 rounded-xl bg-wb-canvas p-3.5">
            <OrderThumbnail url={detail.thumbnailUrl} alt={detail.productName} className="h-16 w-16 shrink-0" />
            <div className="min-w-0 flex-1 space-y-1">
              <StatusPill tone={ORDER_STATUS_TONE[detail.status]}>{ORDER_STATUS_LABEL[detail.status]}</StatusPill>
              <p className="truncate text-sm font-bold">{detail.productName || detail.groupBuyTitle}</p>
              {detail.groupBuyTitle && (
                <p className="truncate text-xs text-wb-secondary">{detail.groupBuyTitle}</p>
              )}
            </div>
          </div>

          {showStepper && (
            <div className="rounded-xl bg-wb-canvas p-4">
              <p className="mb-4 text-sm font-bold">진행 현황</p>
              <div className="flex items-center">
                {STEPS.map((step, index) => {
                  const Icon = step.icon;
                  const reached = index <= stepIndex;
                  return (
                    <div key={step.status} className="flex flex-1 flex-col items-center gap-1.5">
                      <div
                        className={`flex h-8 w-8 items-center justify-center rounded-full ${
                          reached ? "bg-wb-light-green text-wb-green" : "bg-wb-surface text-wb-secondary"
                        }`}
                      >
                        <Icon className="h-3.5 w-3.5" />
                      </div>
                      <span
                        className={`text-center text-[10px] font-semibold ${
                          reached ? "text-wb-green" : "text-wb-secondary"
                        }`}
                      >
                        {step.label}
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          <div className="space-y-2.5 rounded-xl bg-wb-canvas p-4 text-sm">
            <div className="flex justify-between">
              <span className="text-wb-secondary">수량</span>
              <span className="font-bold">{detail.quantity}개</span>
            </div>
            {detail.unitPrice != null && (
              <div className="flex justify-between">
                <span className="text-wb-secondary">개당 가격</span>
                <span className="font-bold">{won(detail.unitPrice)}</span>
              </div>
            )}
            <div className="flex justify-between">
              <span className="text-wb-secondary">상품 금액</span>
              <span className="font-bold">{won(itemAmount)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-wb-secondary">배송비</span>
              <span className="font-bold">무료</span>
            </div>
            <div className="flex justify-between border-t border-wb-line pt-2.5 text-base font-bold">
              <span>총 결제 금액</span>
              <span>{won(detail.totalPrice)}</span>
            </div>
          </div>

          <dl className="space-y-2.5 rounded-xl bg-wb-canvas p-4 text-sm">
            <Row label="배송지" value={detail.shippingAddress} />
            <Row
              label="결제 수단"
              value={detail.pgProvider ? PG_PROVIDER_LABEL[detail.pgProvider] ?? detail.pgProvider : "-"}
            />
            <Row label="승인 일시" value={detail.approvedAt ? formatDateTime(detail.approvedAt) : "-"} />
            <Row label="주문번호" value={detail.orderId} />
            {detail.pgTransactionId && <Row label="거래번호" value={detail.pgTransactionId} />}
          </dl>

          {detail.status === "PAYMENT_FAILED" && (
            <p className="rounded-xl bg-red-600/10 p-3.5 text-sm font-semibold text-red-600 dark:text-red-400">
              결제가 실패한 주문입니다.
            </p>
          )}
        </div>
      )}
    </Modal>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="shrink-0 text-wb-secondary">{label}</dt>
      <dd className="break-all text-right font-medium">{value}</dd>
    </div>
  );
}
