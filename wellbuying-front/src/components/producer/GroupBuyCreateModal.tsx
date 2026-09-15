"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { SelectField } from "@/components/ui/SelectField";
import { TextField } from "@/components/ui/TextField";
import { createGroupBuy } from "@/lib/api/groupBuy";
import { ApiError } from "@/lib/api/http";
import { listMyProducts } from "@/lib/api/product";
import type { GroupBuyPriceTier, ProductMineResponse } from "@/lib/api/types";

type TierInput = { thresholdQuantity: number | ""; unitPrice: number | "" };

function defaultTiers(): [TierInput, TierInput, TierInput] {
  return [
    { thresholdQuantity: "", unitPrice: "" },
    { thresholdQuantity: "", unitPrice: "" },
    { thresholdQuantity: "", unitPrice: "" },
  ];
}

export function GroupBuyCreateModal({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [products, setProducts] = useState<ProductMineResponse[]>([]);
  const [productsLoading, setProductsLoading] = useState(true);
  const [productId, setProductId] = useState<number | null>(null);
  const [title, setTitle] = useState("");
  const [startAt, setStartAt] = useState("");
  const [endAt, setEndAt] = useState("");
  const [minQuantity, setMinQuantity] = useState<number | "">("");
  const [maxQuantity, setMaxQuantity] = useState<number | "">("");
  const [tiers, setTiers] = useState<[TierInput, TierInput, TierInput]>(defaultTiers());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    let ignore = false;

    async function loadProducts() {
      setProductsLoading(true);
      try {
        const list = (await listMyProducts()).content.filter((product) => product.status === "APPROVED");
        if (!ignore) {
          setProducts(list);
          setProductId((current) => current ?? list[0]?.id ?? null);
        }
      } catch {
        if (!ignore) setProducts([]);
      } finally {
        if (!ignore) setProductsLoading(false);
      }
    }

    loadProducts();
    return () => {
      ignore = true;
    };
  }, [open]);

  function updateTier(index: number, patch: Partial<TierInput>) {
    setTiers((prev) => {
      const next = [...prev] as [TierInput, TierInput, TierInput];
      next[index] = { ...next[index], ...patch };
      return next;
    });
  }

  function reset() {
    setProductId(products[0]?.id ?? null);
    setTitle("");
    setStartAt("");
    setEndAt("");
    setMinQuantity("");
    setMaxQuantity("");
    setTiers(defaultTiers());
    setError(null);
  }

  async function handleSubmit() {
    setError(null);
    if (!productId) {
      setError("공동구매를 열 상품을 선택해주세요.");
      return;
    }
    if (!title || !startAt || !endAt) {
      setError("제목과 시작/마감 일시를 입력해주세요.");
      return;
    }
    if (minQuantity === "" || maxQuantity === "") {
      setError("최소/최대 수량을 입력해주세요.");
      return;
    }
    if (tiers.some((tier) => tier.thresholdQuantity === "" || tier.unitPrice === "")) {
      setError("가격 구간의 기준 수량과 판매 단가를 모두 입력해주세요.");
      return;
    }
    const resolvedTiers = tiers.map((tier) => ({
      thresholdQuantity: tier.thresholdQuantity as number,
      unitPrice: tier.unitPrice as number,
    }));
    if (resolvedTiers[0].thresholdQuantity <= 0 || resolvedTiers[0].unitPrice <= 0) {
      setError("기준 수량과 판매 단가는 0보다 커야 해요.");
      return;
    }
    for (let i = 0; i < resolvedTiers.length - 1; i += 1) {
      if (resolvedTiers[i].thresholdQuantity >= resolvedTiers[i + 1].thresholdQuantity) {
        setError("다음 구간의 기준 수량은 이전 구간보다 커야 해요.");
        return;
      }
      if (resolvedTiers[i].unitPrice <= resolvedTiers[i + 1].unitPrice) {
        setError("다음 구간의 판매 단가는 이전 구간보다 저렴해야 해요.");
        return;
      }
    }
    setSubmitting(true);
    try {
      const priceTiers: GroupBuyPriceTier[] = resolvedTiers.map((tier, index) => ({
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
      title="새 공동구매 개설"
      subtitle="상품 정보와 가격 구간을 입력해주세요."
      width="560px"
    >
      <div className="space-y-4">
        <div>
          <span className="mb-1 block text-xs font-bold">상품</span>
          {productsLoading ? (
            <p className="text-sm text-wb-secondary">불러오는 중...</p>
          ) : products.length === 0 ? (
            <div className="rounded-lg bg-wb-canvas p-3 text-sm text-wb-secondary">
              승인 완료된 상품이 없어요.{" "}
              <Link href="/producer/products" className="font-semibold text-wb-green hover:underline">
                상품을 등록하고 승인을 기다려주세요
              </Link>
              .
            </div>
          ) : (
            <SelectField
              placeholder="상품 선택"
              value={productId ? String(productId) : ""}
              onChange={(value) => setProductId(Number(value))}
              options={products.map((product) => ({
                value: String(product.id),
                label: `${product.productName} (${product.startPrice.toLocaleString()}원)`,
              }))}
            />
          )}
        </div>
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
            inputMode="numeric"
            value={String(minQuantity)}
            onChange={(e) => {
              const digitsOnly = e.target.value.replace(/[^0-9]/g, "");
              setMinQuantity(digitsOnly === "" ? "" : Number(digitsOnly));
            }}
          />
          <TextField
            label="최대 수량"
            inputMode="numeric"
            value={String(maxQuantity)}
            onChange={(e) => {
              const digitsOnly = e.target.value.replace(/[^0-9]/g, "");
              setMaxQuantity(digitsOnly === "" ? "" : Number(digitsOnly));
            }}
          />
        </div>

        <div>
          <p className="mb-2 text-xs font-bold">가격 구간 (수량이 늘어날수록 낮아지는 가격)</p>
          <div className="space-y-2.5">
            {tiers.map((tier, index) => (
              <div key={index} className="grid grid-cols-2 gap-2.5 rounded-lg bg-wb-canvas p-3">
                <TextField
                  label={`${index + 1}단계 기준 수량`}
                  inputMode="numeric"
                  value={String(tier.thresholdQuantity)}
                  onChange={(e) => {
                    const digitsOnly = e.target.value.replace(/[^0-9]/g, "");
                    updateTier(index, { thresholdQuantity: digitsOnly === "" ? "" : Number(digitsOnly) });
                  }}
                />
                <TextField
                  label="판매 단가(원)"
                  inputMode="numeric"
                  value={String(tier.unitPrice)}
                  onChange={(e) => {
                    const digitsOnly = e.target.value.replace(/[^0-9]/g, "");
                    updateTier(index, { unitPrice: digitsOnly === "" ? "" : Number(digitsOnly) });
                  }}
                />
              </div>
            ))}
          </div>
        </div>

        {error && <Banner tone="error">{error}</Banner>}

        <Button
          className="w-full"
          loading={submitting}
          disabled={!productsLoading && products.length === 0}
          onClick={handleSubmit}
        >
          공동구매 생성
        </Button>
      </div>
    </Modal>
  );
}
