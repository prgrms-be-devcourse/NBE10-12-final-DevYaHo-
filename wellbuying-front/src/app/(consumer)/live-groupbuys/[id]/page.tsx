"use client";

import { useCallback, useEffect, useState } from "react";
import { notFound, useParams } from "next/navigation";
import { GroupBuyStatusTag } from "@/components/groupbuy/GroupBuyStatusTag";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { TextField } from "@/components/ui/TextField";
import {
  cancelGroupBuyParticipation,
  getGroupBuy,
  getGroupBuyStatus,
  getMyGroupBuyParticipation,
  participateInGroupBuy,
} from "@/lib/api/groupBuy";
import { ApiError } from "@/lib/api/http";
import type {
  GroupBuyDetailResponse,
  GroupBuyPartMeResponse,
  GroupBuyStatusResponse,
} from "@/lib/api/types";
import { formatDateTime, formatRemaining, won } from "@/lib/format";
import { resolveCurrentUnitPrice } from "@/lib/groupBuyPricing";

export default function LiveGroupBuyDetailPage() {
  const params = useParams<{ id: string }>();
  const groupBuyId = Number(params.id);

  const [detail, setDetail] = useState<GroupBuyDetailResponse | null>(null);
  const [status, setStatus] = useState<GroupBuyStatusResponse | null>(null);
  const [myPart, setMyPart] = useState<GroupBuyPartMeResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [resourceNotFound, setResourceNotFound] = useState(false);

  const [quantity, setQuantity] = useState(1);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const reload = useCallback(async () => {
    const [detailRes, statusRes, myPartRes] = await Promise.all([
      getGroupBuy(groupBuyId),
      getGroupBuyStatus(groupBuyId),
      getMyGroupBuyParticipation(groupBuyId),
    ]);
    setDetail(detailRes);
    setStatus(statusRes);
    setMyPart(myPartRes);
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
        if (ignore) return;
        if (e instanceof ApiError && e.status === 404) {
          setResourceNotFound(true);
        } else {
          setLoadError(e instanceof ApiError ? e.message : "공동구매 정보를 불러오지 못했어요.");
        }
      } finally {
        if (!ignore) setLoading(false);
      }
    }

    load();
    return () => {
      ignore = true;
    };
  }, [groupBuyId, reload]);

  async function handleParticipate() {
    setActionError(null);
    setActionMessage(null);
    setSubmitting(true);
    try {
      await participateInGroupBuy(groupBuyId, { quantity });
      await reload();
      setActionMessage("참여가 완료됐어요.");
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : "참여 처리 중 오류가 발생했어요.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCancelParticipation(partId: number) {
    setActionError(null);
    setActionMessage(null);
    setSubmitting(true);
    try {
      await cancelGroupBuyParticipation(groupBuyId, partId);
      await reload();
      setActionMessage("참여를 취소했어요.");
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : "참여 취소 중 오류가 발생했어요.");
    } finally {
      setSubmitting(false);
    }
  }

  if (!Number.isFinite(groupBuyId) || resourceNotFound) {
    notFound();
  }

  if (loading) {
    return <div className="p-9 text-sm text-wb-secondary">불러오는 중...</div>;
  }

  if (loadError || !detail || !status) {
    return (
      <div className="mx-auto max-w-3xl px-6 py-9">
        <Banner tone="error">{loadError ?? "공동구매 정보를 불러오지 못했어요."}</Banner>
      </div>
    );
  }

  const canParticipate = status.status === "ONGOING" && !myPart?.participated;

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-6 py-9">
      <div className="space-y-3 rounded-2xl border border-wb-line bg-wb-surface p-6">
        <div className="flex items-center gap-2">
          <GroupBuyStatusTag status={status.status} />
          <span className="text-xs text-wb-secondary">상품 #{detail.productId} · 생산자 #{detail.producerId}</span>
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
        <h2 className="text-lg font-bold">함께할수록 내려가는 가격</h2>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          {(() => {
            const sortedTiers = [...detail.priceTiers].sort(
              (a, b) => a.thresholdQuantity - b.thresholdQuantity,
            );
            const reachedTiers = sortedTiers.filter((t) => t.thresholdQuantity <= status.currentQuantity);
            const activeTierOrder = reachedTiers.at(-1)?.tierOrder ?? sortedTiers[0]?.tierOrder;

            return sortedTiers.map((tier) => {
              const active = tier.tierOrder === activeTierOrder;
              return (
                <div
                  key={tier.tierOrder}
                  className={`rounded-xl p-4 ${active ? "bg-wb-light-green/60" : "bg-wb-canvas"}`}
                >
                  <p className={`text-xs font-semibold ${active ? "text-wb-green" : "text-wb-secondary"}`}>
                    {tier.thresholdQuantity.toLocaleString("ko-KR")}개부터
                  </p>
                  <p className="mt-1.5 text-lg font-bold">{won(tier.unitPrice)}</p>
                </div>
              );
            });
          })()}
        </div>
      </div>

      <div className="space-y-4 rounded-2xl border border-wb-line bg-wb-surface p-6">
        <h2 className="text-lg font-bold">내 참여</h2>

        {myPart?.participated && myPart.part ? (
          <div className="space-y-3">
            <p className="text-sm text-wb-secondary">
              {myPart.part.quantity.toLocaleString("ko-KR")}개 ·{" "}
              {myPart.part.appliedPrice !== null ? (
                <>확정 단가 {won(myPart.part.appliedPrice)}</>
              ) : (
                (() => {
                  const estimated = resolveCurrentUnitPrice(detail.priceTiers, status.currentQuantity);
                  return (
                    <>
                      현재 예상가 {estimated !== null ? won(estimated) : "-"}
                      <span className="text-xs"> (공동구매 성사 시 전원 동일한 최종가로 확정돼요)</span>
                    </>
                  );
                })()
              )}
            </p>
            {status.status === "ONGOING" && myPart.part.status === "CONFIRMED" && (
              <Button
                variant="secondary"
                loading={submitting}
                onClick={() => handleCancelParticipation(myPart.part!.id)}
              >
                참여 취소
              </Button>
            )}
          </div>
        ) : (
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <TextField
              label="참여 수량"
              type="number"
              min={1}
              value={quantity}
              onChange={(e) => setQuantity(Math.max(1, Number(e.target.value)))}
              className="sm:w-32"
            />
            <Button disabled={!canParticipate} loading={submitting} onClick={handleParticipate}>
              {status.status !== "ONGOING" ? "참여할 수 없어요" : "참여하기"}
            </Button>
          </div>
        )}

        {actionMessage && <Banner tone="success">{actionMessage}</Banner>}
        {actionError && <Banner tone="error">{actionError}</Banner>}
      </div>
    </div>
  );
}
