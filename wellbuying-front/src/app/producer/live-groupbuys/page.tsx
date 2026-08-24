"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Plus, RadioTower } from "lucide-react";
import { GroupBuyStatusTag } from "@/components/groupbuy/GroupBuyStatusTag";
import { LiveGroupBuyCreateModal } from "@/components/producer/LiveGroupBuyCreateModal";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { EmptyState } from "@/components/ui/EmptyState";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { useAuth } from "@/lib/auth/AuthProvider";
import { cancelGroupBuy, listGroupBuys } from "@/lib/api/groupBuy";
import { ApiError } from "@/lib/api/http";
import type { GroupBuySummaryResponse } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";

export default function ProducerLiveGroupBuysPage() {
  const { member } = useAuth();
  const [items, setItems] = useState<GroupBuySummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [cancelTargetId, setCancelTargetId] = useState<number | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await listGroupBuys({ size: 100 });
      setItems(page.content.filter((item) => item.producerId === member?.memberId));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "목록을 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  }, [member?.memberId]);

  useEffect(() => {
    async function load() {
      await reload();
    }
    load();
  }, [reload]);

  async function handleCancel(id: number) {
    setError(null);
    try {
      await cancelGroupBuy(id);
      await reload();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "취소 처리 중 오류가 발생했어요.");
    }
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <RadioTower className="h-6 w-6 text-wb-green" />
          <div>
            <h1 className="text-3xl font-bold">라이브 공동구매 관리</h1>
            <p className="mt-1 text-sm text-wb-secondary">실제 백엔드 API에 연결된 내 공동구매예요.</p>
          </div>
        </div>
        <Button onClick={() => setShowCreate(true)}>
          <Plus className="h-4 w-4" /> 공동구매 개설
        </Button>
      </div>

      {error && <Banner tone="error">{error}</Banner>}

      {loading ? (
        <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : items.length === 0 ? (
        <EmptyState
          icon={RadioTower}
          title="아직 만든 공동구매가 없어요"
          message="공동구매 개설 버튼을 눌러 실제 서버에 첫 공동구매를 만들어보세요."
        />
      ) : (
        <div className="space-y-4">
          {items.map((item) => (
            <div key={item.id} className="space-y-4 rounded-2xl border border-wb-line bg-wb-surface p-5">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  <div className="mb-1.5 flex gap-2">
                    <GroupBuyStatusTag status={item.status} />
                    <span className="text-xs text-wb-secondary">상품 #{item.productId}</span>
                  </div>
                  <Link href={`/live-groupbuys/${item.id}`} className="line-clamp-2 text-lg font-bold hover:underline">
                    {item.title}
                  </Link>
                  <p className="mt-1 text-xs text-wb-secondary">
                    {formatDateTime(item.startAt)} ~ {formatDateTime(item.endAt)}
                  </p>
                </div>
                <p className="text-sm font-bold sm:text-right">
                  {item.currentQuantity.toLocaleString("ko-KR")} / {item.maxQuantity.toLocaleString("ko-KR")}개
                </p>
              </div>

              <ProgressBar value={item.maxQuantity === 0 ? 0 : item.currentQuantity / item.maxQuantity} />

              {item.status === "READY" && (
                <button
                  onClick={() => setCancelTargetId(item.id)}
                  className="text-sm font-semibold text-red-600"
                >
                  공동구매 취소
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      <LiveGroupBuyCreateModal open={showCreate} onClose={() => setShowCreate(false)} onCreated={reload} />

      <ConfirmDialog
        open={cancelTargetId !== null}
        onClose={() => setCancelTargetId(null)}
        onConfirm={() => cancelTargetId !== null && handleCancel(cancelTargetId)}
        title="공동구매를 취소할까요?"
        message="시작 전(오픈 예정) 상태에서만 취소할 수 있어요."
        confirmLabel="공동구매 취소"
        destructive
      />
    </div>
  );
}
