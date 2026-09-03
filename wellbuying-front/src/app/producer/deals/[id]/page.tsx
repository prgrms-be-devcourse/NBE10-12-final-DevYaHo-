"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { GroupBuyStatusTag } from "@/components/groupbuy/GroupBuyStatusTag";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { TextField } from "@/components/ui/TextField";
import { cancelGroupBuy, getGroupBuy, getGroupBuyStatus, updateGroupBuy } from "@/lib/api/groupBuy";
import { ApiError } from "@/lib/api/http";
import type { GroupBuyDetailResponse, GroupBuyStatusResponse } from "@/lib/api/types";
import { formatDateTime, formatRemaining, won } from "@/lib/format";

export default function ProducerDealDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const groupBuyId = Number(params.id);

  const [detail, setDetail] = useState<GroupBuyDetailResponse | null>(null);
  const [status, setStatus] = useState<GroupBuyStatusResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [editTitle, setEditTitle] = useState("");
  const [editEndAt, setEditEndAt] = useState("");
  const [saving, setSaving] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [cancelOpen, setCancelOpen] = useState(false);

  const reload = useCallback(async () => {
    const [detailRes, statusRes] = await Promise.all([getGroupBuy(groupBuyId), getGroupBuyStatus(groupBuyId)]);
    setDetail(detailRes);
    setStatus(statusRes);
    setEditTitle(detailRes.title);
    setEditEndAt(detailRes.endAt.slice(0, 16));
  }, [groupBuyId]);

  useEffect(() => {
    if (!Number.isFinite(groupBuyId)) return;
    let ignore = false;

    async function load() {
      setLoading(true);
      setLoadError(null);
      try {
        await reload();
      } catch (e) {
        if (!ignore) setLoadError(e instanceof ApiError ? e.message : "공동구매 정보를 불러오지 못했어요.");
      } finally {
        if (!ignore) setLoading(false);
      }
    }

    load();
    return () => {
      ignore = true;
    };
  }, [groupBuyId, reload]);

  async function handleSave() {
    setActionError(null);
    setActionMessage(null);
    setSaving(true);
    try {
      await updateGroupBuy(groupBuyId, { title: editTitle, endAt: editEndAt });
      await reload();
      setActionMessage("수정됐어요.");
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : "수정 중 오류가 발생했어요.");
    } finally {
      setSaving(false);
    }
  }

  async function handleCancel() {
    setActionError(null);
    try {
      await cancelGroupBuy(groupBuyId);
      router.push("/producer/deals");
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : "취소 중 오류가 발생했어요.");
    }
  }

  if (!Number.isFinite(groupBuyId)) {
    return <div className="p-9 text-sm text-wb-secondary">잘못된 공동구매 주소예요.</div>;
  }

  if (loading) {
    return <div className="p-9 text-sm text-wb-secondary">불러오는 중...</div>;
  }

  if (loadError || !detail || !status) {
    return (
      <div className="mx-auto max-w-3xl px-6 py-9">
        <Banner tone="error">{loadError ?? "공동구매를 찾을 수 없어요."}</Banner>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-6 py-9">
      <div className="space-y-3 rounded-2xl border border-wb-line bg-wb-surface p-6">
        <div className="flex items-center gap-2">
          <GroupBuyStatusTag status={status.status} />
          <span className="text-xs text-wb-secondary">{detail.productName}</span>
        </div>
        <h1 className="text-2xl font-bold">{detail.title}</h1>
        <p className="text-xs text-wb-secondary">
          {formatDateTime(detail.startAt)} ~ {formatDateTime(detail.endAt)}
        </p>

        <ProgressBar value={detail.maxQuantity === 0 ? 0 : status.currentQuantity / detail.maxQuantity} />
        <div className="grid grid-cols-3 gap-3 text-center text-sm">
          <div>
            <p className="text-lg font-bold">{status.currentQuantity.toLocaleString("ko-KR")}</p>
            <p className="text-xs text-wb-secondary">현재 수량</p>
          </div>
          <div>
            <p className="text-lg font-bold">{status.participantCount.toLocaleString("ko-KR")}</p>
            <p className="text-xs text-wb-secondary">참여자 수</p>
          </div>
          <div>
            <p className="text-lg font-bold">{formatRemaining(status.remainingSeconds)}</p>
            <p className="text-xs text-wb-secondary">남은 시간</p>
          </div>
        </div>
      </div>

      <div className="space-y-3 rounded-2xl border border-wb-line bg-wb-surface p-6">
        <h2 className="text-lg font-bold">가격 구간</h2>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          {[...detail.priceTiers]
            .sort((a, b) => a.thresholdQuantity - b.thresholdQuantity)
            .map((tier) => (
              <div key={tier.tierOrder} className="rounded-xl bg-wb-canvas p-4">
                <p className="text-xs font-semibold text-wb-secondary">
                  {tier.thresholdQuantity.toLocaleString("ko-KR")}개부터
                </p>
                <p className="mt-1.5 text-lg font-bold">{won(tier.unitPrice)}</p>
              </div>
            ))}
        </div>
      </div>

      <div className="space-y-4 rounded-2xl border border-wb-line bg-wb-surface p-6">
        <h2 className="text-lg font-bold">정보 수정</h2>
        <TextField label="제목" value={editTitle} onChange={(e) => setEditTitle(e.target.value)} />
        <TextField
          label="마감 일시"
          type="datetime-local"
          value={editEndAt}
          onChange={(e) => setEditEndAt(e.target.value)}
        />
        <div className="flex flex-wrap items-center gap-3">
          <Button loading={saving} onClick={handleSave}>
            저장
          </Button>
          {status.status === "READY" && (
            <Button variant="secondary" onClick={() => setCancelOpen(true)}>
              공동구매 취소
            </Button>
          )}
        </div>

        {actionMessage && <Banner tone="success">{actionMessage}</Banner>}
        {actionError && <Banner tone="error">{actionError}</Banner>}
      </div>

      <ConfirmDialog
        open={cancelOpen}
        onClose={() => setCancelOpen(false)}
        onConfirm={handleCancel}
        title="공동구매를 취소할까요?"
        message="시작 전(오픈 예정) 상태에서만 취소할 수 있어요."
        confirmLabel="공동구매 취소"
        destructive
      />
    </div>
  );
}
