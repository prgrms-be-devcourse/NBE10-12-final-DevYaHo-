"use client";

import { useCallback, useEffect, useState } from "react";
import { CreditCard } from "lucide-react";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { getBillingKey, requestBillingKeyCustomerKey } from "@/lib/api/billingKey";
import { ApiError } from "@/lib/api/http";
import type { BillingKeyResponse } from "@/lib/api/types";
import { won } from "@/lib/format";
import { savePendingParticipation, type PendingParticipation } from "@/lib/payments/pendingParticipation";
import { loadTossPayments } from "@/lib/toss/loadTossPayments";

// 참여하기를 누르면 뜨는 결제 정보 확인 창.
// 공동구매는 성사 시점에 서버가 빌링키로 자동 결제하므로, 이 화면에서 돈이 빠져나가지는 않는다.
// 여기서 하는 일은 "성사되면 결제할 카드"를 확보하는 것뿐이다.
export function PaymentMethodModal({
  open,
  onClose,
  title,
  unitPrice,
  pending,
  submitting,
  onConfirm,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  unitPrice: number | null;
  pending: PendingParticipation;
  submitting: boolean;
  onConfirm: () => void;
}) {
  const [billingKey, setBillingKey] = useState<BillingKeyResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const clientKey = process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY;

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setBillingKey(await getBillingKey());
    } catch (e) {
      setBillingKey(null);
      setError(
        e instanceof ApiError && e.status === 401
          ? "로그인이 만료됐어요. 다시 로그인한 뒤 시도해주세요."
          : e instanceof ApiError
            ? e.message
            : "결제 수단을 불러오지 못했어요.",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  // 닫았다 다시 열면 그 사이 바뀐 카드 정보를 반영해야 하므로 열릴 때마다 조회한다
  useEffect(() => {
    if (!open) return;
    void reload();
  }, [open, reload]);

  async function handleRegisterCard() {
    if (!clientKey) {
      setError("NEXT_PUBLIC_TOSS_CLIENT_KEY가 설정되지 않았어요.");
      return;
    }
    setWorking(true);
    setError(null);
    try {
      // 토스 결제창으로 넘어가면 이 페이지의 입력값이 전부 사라지므로 먼저 보관한다
      savePendingParticipation(pending);

      // customerKey는 발급 때와 승인 때 값이 같아야 해서 프런트가 만들지 않고 서버 값을 그대로 쓴다
      const { customerKey } = await requestBillingKeyCustomerKey();

      const toss = await loadTossPayments(clientKey);
      const origin = window.location.origin;
      const callbackUrl = `${origin}/deals/${pending.groupBuyId}/billing-callback`;
      await toss.requestBillingAuth("카드", {
        customerKey,
        successUrl: callbackUrl,
        failUrl: callbackUrl,
      });
      // 성공하면 successUrl로 리다이렉트되므로 아래는 실행되지 않는다
    } catch (e) {
      // 사용자가 결제창을 그냥 닫아도 여기로 들어온다
      setError(e instanceof Error ? e.message : "카드 등록 창을 여는 데 실패했어요.");
      setWorking(false);
    }
  }

  const registered = billingKey?.registered === true;
  const estimatedTotal = unitPrice === null ? null : unitPrice * pending.quantity;

  return (
    <Modal open={open} onClose={onClose} title="결제 정보 확인" subtitle={title}>
      <div className="space-y-5">
        <div className="space-y-2.5 rounded-xl bg-wb-canvas p-4 text-sm">
          <div className="flex justify-between">
            <span className="text-wb-secondary">수량</span>
            <span className="font-bold">{pending.quantity.toLocaleString("ko-KR")}개</span>
          </div>
          <div className="flex justify-between">
            <span className="text-wb-secondary">현재 단가</span>
            <span className="font-bold">{unitPrice !== null ? won(unitPrice) : "-"}</span>
          </div>
          <div className="flex justify-between border-t border-wb-line pt-2.5 text-base">
            <span className="font-bold">예상 결제 금액</span>
            <span className="font-bold text-wb-green">
              {estimatedTotal !== null ? won(estimatedTotal) : "-"}
            </span>
          </div>
        </div>

        <div className="space-y-1 text-xs text-wb-secondary">
          <p className="font-semibold text-wb-ink">배송지</p>
          <p>
            [{pending.zipcode}] {pending.address} {pending.addressDetail}
          </p>
        </div>

        <div className="space-y-3 border-t border-wb-line pt-4">
          <p className="text-sm font-semibold">결제 수단</p>
          {loading ? (
            <p className="text-sm text-wb-secondary">결제 수단을 확인하는 중...</p>
          ) : registered ? (
            <div className="flex items-center gap-3 rounded-xl border border-wb-line p-4">
              <CreditCard className="h-5 w-5 shrink-0 text-wb-green" />
              <div className="text-sm">
                <p className="font-semibold">{billingKey?.cardCompany ?? "등록된 카드"}</p>
                <p className="text-xs text-wb-secondary">
                  {billingKey?.cardLast4 ? `****${billingKey.cardLast4}` : "카드 정보 없음"}
                </p>
              </div>
            </div>
          ) : (
            <div className="rounded-xl border border-dashed border-wb-line p-4 text-sm text-wb-secondary">
              등록된 카드가 없어요. 카드를 등록해야 공동구매가 성사됐을 때 자동으로 결제돼요.
            </div>
          )}
        </div>

        {error && <Banner tone="error">{error}</Banner>}

        {registered ? (
          <Button className="w-full" loading={submitting} onClick={onConfirm}>
            {estimatedTotal !== null ? `${won(estimatedTotal)}으로 참여하기` : "참여하기"}
          </Button>
        ) : (
          <Button className="w-full" loading={working} disabled={loading} onClick={handleRegisterCard}>
            카드 등록하고 참여하기
          </Button>
        )}

        <p className="text-center text-xs text-wb-secondary">
          지금 결제되지 않아요 · 공동구매가 성사되면 최종가로 자동 결제됩니다
        </p>
      </div>
    </Modal>
  );
}
