"use client";

import { useState } from "react";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { createGroupBuy } from "@/lib/api/groupBuy";
import { ApiError } from "@/lib/api/http";
import type { GroupBuyPriceTier } from "@/lib/api/types";

type TierInput = { thresholdQuantity: number; unitPrice: number };

function defaultTiers(): [TierInput, TierInput, TierInput] {
  return [
    { thresholdQuantity: 100, unitPrice: 15_000 },
    { thresholdQuantity: 1_000, unitPrice: 12_000 },
    { thresholdQuantity: 10_000, unitPrice: 10_000 },
  ];
}

export function LiveGroupBuyCreateModal({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [productId, setProductId] = useState(1);
  const [title, setTitle] = useState("");
  const [startAt, setStartAt] = useState("");
  const [endAt, setEndAt] = useState("");
  const [minQuantity, setMinQuantity] = useState(100);
  const [maxQuantity, setMaxQuantity] = useState(10_000);
  const [tiers, setTiers] = useState<[TierInput, TierInput, TierInput]>(defaultTiers());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function updateTier(index: number, patch: Partial<TierInput>) {
    setTiers((prev) => {
      const next = [...prev] as [TierInput, TierInput, TierInput];
      next[index] = { ...next[index], ...patch };
      return next;
    });
  }

  function reset() {
    setProductId(1);
    setTitle("");
    setStartAt("");
    setEndAt("");
    setMinQuantity(100);
    setMaxQuantity(10_000);
    setTiers(defaultTiers());
    setError(null);
  }

  async function handleSubmit() {
    setError(null);
    if (!title || !startAt || !endAt) {
      setError("제목과 시작/마감 일시를 입력해주세요.");
      return;
    }
    setSubmitting(true);
    try {
      const priceTiers: GroupBuyPriceTier[] = tiers.map((tier, index) => ({
        tierOrder: index + 1,
        thresholdQuantity: tier.thresholdQuantity,
        unitPrice: tier.unitPrice,
      }));
      await createGroupBuy({
        productId,
        title,
        startAt,
        endAt,
        minQuantity,
        maxQuantity,
        priceTiers,
      });
      reset();
      onCreated();
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "공동구매 생성 중 오류가 발생했어요.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={() => {
        reset();
        onClose();
      }}
      title="새 공동구매 개설 (실서버 연동)"
      subtitle="실제 백엔드 API(POST /api/groupBuys)로 생성됩니다."
      width="560px"
    >
      <div className="space-y-4">
        <TextField
          label="상품 ID"
          type="number"
          min={1}
          value={productId}
          onChange={(e) => setProductId(Number(e.target.value))}
        />
        <TextField label="제목" value={title} onChange={(e) => setTitle(e.target.value)} />

        <div className="grid grid-cols-2 gap-3">
          <TextField
            label="시작 일시"
            type="datetime-local"
            value={startAt}
            onChange={(e) => setStartAt(e.target.value)}
          />
          <TextField
            label="마감 일시"
            type="datetime-local"
            value={endAt}
            onChange={(e) => setEndAt(e.target.value)}
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <TextField
            label="최소 수량"
            type="number"
            min={1}
            value={minQuantity}
            onChange={(e) => setMinQuantity(Number(e.target.value))}
          />
          <TextField
            label="최대 수량"
            type="number"
            min={1}
            value={maxQuantity}
            onChange={(e) => setMaxQuantity(Number(e.target.value))}
          />
        </div>

        <div>
          <p className="mb-2 text-xs font-bold">가격 구간 (수량이 늘어날수록 낮아지는 가격)</p>
          <div className="space-y-2.5">
            {tiers.map((tier, index) => (
              <div key={index} className="grid grid-cols-2 gap-2.5 rounded-lg bg-wb-canvas p-3">
                <label className="block">
                  <span className="mb-1 block text-[10px] text-wb-secondary">{index + 1}단계 기준 수량</span>
                  <input
                    type="number"
                    min={1}
                    value={tier.thresholdQuantity}
                    onChange={(e) => updateTier(index, { thresholdQuantity: Number(e.target.value) })}
                    className="w-full rounded-md border border-wb-line bg-wb-surface px-2 py-1.5 text-sm font-bold outline-none"
                  />
                </label>
                <label className="block">
                  <span className="mb-1 block text-[10px] text-wb-secondary">판매 단가(원)</span>
                  <input
                    type="number"
                    min={1}
                    value={tier.unitPrice}
                    onChange={(e) => updateTier(index, { unitPrice: Number(e.target.value) })}
                    className="w-full rounded-md border border-wb-line bg-wb-surface px-2 py-1.5 text-sm font-bold outline-none"
                  />
                </label>
              </div>
            ))}
          </div>
        </div>

        {error && <Banner tone="error">{error}</Banner>}

        <Button className="w-full" loading={submitting} onClick={handleSubmit}>
          공동구매 생성
        </Button>
      </div>
    </Modal>
  );
}
