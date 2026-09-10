"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { ChevronRight, ShoppingBag } from "lucide-react";
import { AccountShell } from "@/components/account/AccountShell";
import { OrderDetailModal } from "@/components/consumer/OrderDetailModal";
import { OrderThumbnail } from "@/components/consumer/OrderThumbnail";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { StatusPill } from "@/components/ui/Tag";
import { ApiError } from "@/lib/api/http";
import { listMyOrders } from "@/lib/api/orders";
import type { OrderSummaryResponse } from "@/lib/api/types";
import { won } from "@/lib/format";
import { ORDER_STATUS_LABEL, ORDER_STATUS_TONE } from "@/lib/order/orderStatus";

const PAGE_SIZE = 10;

function OrdersContent() {
  const searchParams = useSearchParams();
  const [orders, setOrders] = useState<OrderSummaryResponse[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // 알림을 클릭해 들어온 경우 ?orderId=로 바로 상세를 연다
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(() => searchParams.get("orderId"));

  // 이 페이지를 이미 보고 있는 상태에서 알림(NotificationBell은 전역이라 어디서든 클릭 가능)을 또 클릭하면
  // 같은 라우트라 리마운트 없이 쿼리스트링만 바뀌므로, 위 초기값만으로는 새 orderId를 못 따라간다 -
  // searchParams 변화를 별도로 구독해 갱신한다
  useEffect(() => {
    const orderId = searchParams.get("orderId");
    if (orderId) setSelectedOrderId(orderId);
  }, [searchParams]);

  useEffect(() => {
    let cancelled = false;
    async function loadFirstPage() {
      setLoading(true);
      setError(null);
      try {
        const res = await listMyOrders({ page: 0, size: PAGE_SIZE });
        if (cancelled) return;
        setOrders(res.content);
        setPage(res.page.number);
        setHasNext(res.page.number + 1 < res.page.totalPages);
      } catch (e) {
        if (!cancelled) setError(e instanceof ApiError ? e.message : "결제 내역을 불러오지 못했어요.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void loadFirstPage();
    return () => {
      cancelled = true;
    };
  }, []);

  // 재시도로 새 주문이 생겼을 때 목록에 반영하기 위한 새로고침 - 사용자 액션(버튼 클릭)에 대한 응답이라
  // 마운트 시 fetch와 달리 언마운트 취소 가드는 필요 없다 (loadMore와 동일한 성격)
  async function refreshOrders() {
    setLoading(true);
    setError(null);
    try {
      const res = await listMyOrders({ page: 0, size: PAGE_SIZE });
      setOrders(res.content);
      setPage(res.page.number);
      setHasNext(res.page.number + 1 < res.page.totalPages);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "결제 내역을 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  }

  async function loadMore() {
    setLoadingMore(true);
    setError(null);
    try {
      const res = await listMyOrders({ page: page + 1, size: PAGE_SIZE });
      setOrders((prev) => [...prev, ...res.content]);
      setPage(res.page.number);
      setHasNext(res.page.number + 1 < res.page.totalPages);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "더 불러오지 못했어요.");
    } finally {
      setLoadingMore(false);
    }
  }

  if (loading) {
    return <div className="py-9 text-sm text-wb-secondary">불러오는 중...</div>;
  }

  if (error && orders.length === 0) {
    return <Banner tone="error">{error}</Banner>;
  }

  if (orders.length === 0) {
    return (
      <EmptyState
        icon={ShoppingBag}
        title="결제 내역이 없어요"
        message="공동구매가 성사되어 결제가 진행되면 여기에서 결제 정보를 확인할 수 있어요."
      />
    );
  }

  return (
    <div className="space-y-4">
      <h1 className="text-lg font-bold">결제 내역</h1>

      <div className="space-y-3">
        {orders.map((order) => (
          <button
            key={order.orderId}
            onClick={() => setSelectedOrderId(order.orderId)}
            className="flex w-full items-center gap-4 rounded-xl border border-wb-line bg-wb-surface p-4 text-left hover:shadow-sm"
          >
            <OrderThumbnail url={order.thumbnailUrl} alt={order.productName} className="h-16 w-20 shrink-0" />
            <div className="min-w-0 flex-1 space-y-1.5">
              <StatusPill tone={ORDER_STATUS_TONE[order.status]}>{ORDER_STATUS_LABEL[order.status]}</StatusPill>
              <p className="line-clamp-1 font-bold">{order.productName || order.groupBuyTitle}</p>
              <p className="text-xs text-wb-secondary">
                {order.quantity}개 · {won(order.totalPrice)}
              </p>
            </div>
            <ChevronRight className="h-4 w-4 shrink-0 text-wb-secondary" />
          </button>
        ))}
      </div>

      {error && <Banner tone="error">{error}</Banner>}

      {hasNext && (
        <Button variant="secondary" className="w-full" loading={loadingMore} onClick={() => void loadMore()}>
          더 보기
        </Button>
      )}

      <OrderDetailModal
        orderId={selectedOrderId}
        open={selectedOrderId !== null}
        onClose={() => setSelectedOrderId(null)}
        onRetried={(newOrderId) => {
          setSelectedOrderId(newOrderId);
          void refreshOrders();
        }}
      />
    </div>
  );
}

export default function OrdersPage() {
  return (
    <AccountShell>
      <Suspense fallback={<div className="py-9 text-sm text-wb-secondary">불러오는 중...</div>}>
        <OrdersContent />
      </Suspense>
    </AccountShell>
  );
}
