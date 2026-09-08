"use client";

import { useEffect, useState } from "react";
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
  const [orders, setOrders] = useState<OrderSummaryResponse[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);

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
