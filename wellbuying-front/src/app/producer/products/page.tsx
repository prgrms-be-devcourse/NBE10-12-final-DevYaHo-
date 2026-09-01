"use client";

import { useCallback, useEffect, useState } from "react";
import { Package, Plus } from "lucide-react";
import { ProductCreateModal } from "@/components/producer/ProductCreateModal";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { listMyProducts } from "@/lib/api/product";
import { ApiError } from "@/lib/api/http";
import type { ProductMineResponse } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";

const STATUS_LABEL: Record<ProductMineResponse["status"], string> = {
  PENDING: "승인 대기",
  APPROVED: "판매중",
  REJECTED: "반려됨",
};

const STATUS_TONE: Record<ProductMineResponse["status"], string> = {
  PENDING: "bg-wb-canvas text-wb-secondary",
  APPROVED: "bg-wb-light-green/60 text-wb-green",
  REJECTED: "bg-red-600/12 text-red-600",
};

export default function ProducerProductsPage() {
  const [items, setItems] = useState<ProductMineResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const slice = await listMyProducts();
      setItems(slice.content);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "목록을 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    async function load() {
      await reload();
    }
    load();
  }, [reload]);

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <Package className="h-6 w-6 text-wb-green" />
          <div>
            <h1 className="text-3xl font-bold">상품 관리</h1>
            <p className="mt-1 text-sm text-wb-secondary">
              공동구매를 열려면 먼저 상품을 등록해야 해요.
            </p>
          </div>
        </div>
        <Button onClick={() => setShowCreate(true)}>
          <Plus className="h-4 w-4" /> 상품 등록
        </Button>
      </div>

      {error && <Banner tone="error">{error}</Banner>}

      {loading ? (
        <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : items.length === 0 ? (
        <EmptyState
          icon={Package}
          title="아직 등록한 상품이 없어요"
          message="상품 등록 버튼을 눌러 공동구매를 열 상품을 먼저 등록해보세요."
        />
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {items.map((product) => (
            <div key={product.id} className="space-y-2 rounded-2xl border border-wb-line bg-wb-surface p-5">
              <div className="flex items-center gap-2">
                <span className={`rounded-full px-2.5 py-0.5 text-xs font-bold ${STATUS_TONE[product.status]}`}>
                  {STATUS_LABEL[product.status]}
                </span>
                <span className="text-xs text-wb-secondary">{formatDateTime(product.createdAt)}</span>
              </div>
              <p className="text-lg font-bold">{product.productName}</p>
              <p className="text-sm text-wb-secondary">{product.startPrice.toLocaleString()}원</p>
            </div>
          ))}
        </div>
      )}

      <ProductCreateModal open={showCreate} onClose={() => setShowCreate(false)} onCreated={reload} />
    </div>
  );
}
