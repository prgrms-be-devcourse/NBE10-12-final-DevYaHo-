"use client";

import { useEffect, useRef, useState } from "react";
import { Package, Plus } from "lucide-react";
import { GroupBuyArtwork } from "@/components/deal/GroupBuyArtwork";
import { ProductCreateModal } from "@/components/producer/ProductCreateModal";
import { ProductDeleteModal } from "@/components/producer/ProductDeleteModal";
import { ProductEditModal } from "@/components/producer/ProductEditModal";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { Pagination } from "@/components/ui/Pagination";
import { StatusPill } from "@/components/ui/Tag";
import { listMyProducts } from "@/lib/api/product";
import type { PageResponse, ProductMineResponse } from "@/lib/api/types";
import { formatDateTime, won } from "@/lib/format";
import { resolveCatalogEntry } from "@/lib/groupBuy/seedCatalog";
import { showToast } from "@/lib/toast/toastStore";
import { invalidatePagedQuery, usePagedQuery } from "@/hooks/usePagedQuery";

const STATUS_LABEL: Record<ProductMineResponse["status"], string> = {
  PENDING: "승인 대기",
  APPROVED: "판매중",
  REJECTED: "반려됨",
};

const STATUS_TONE: Record<ProductMineResponse["status"], "orange" | "green" | "red"> = {
  PENDING: "orange",
  APPROVED: "green",
  REJECTED: "red",
};

export default function ProducerProductsPage() {
  const [showCreate, setShowCreate] = useState(false);
  const [editTarget, setEditTarget] = useState<ProductMineResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ProductMineResponse | null>(null);
  const [page, setPage] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [reloadToken, setReloadToken] = useState(0);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  function handleKeywordChange(value: string) {
    setKeyword(value);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setDebouncedKeyword(value);
      setPage(0);
    }, 300);
  }

  useEffect(() => {
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, []);

  const { data, error, loading } = usePagedQuery<PageResponse<ProductMineResponse>>(
    "producer-products",
    { keyword: debouncedKeyword, page, reloadToken },
    () => listMyProducts({ keyword: debouncedKeyword || undefined, page, size: 10 }),
    "목록을 불러오지 못했어요.",
  );
  const items = data?.content ?? null;
  const totalPages = data?.page.totalPages ?? 0;

  function reload() {
    invalidatePagedQuery("producer-products");
    setReloadToken((t) => t + 1);
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-bold tracking-wide text-wb-green">PRODUCTS</p>
          <h1 className="mt-1 text-3xl font-bold">상품 관리</h1>
          <p className="mt-1 text-sm text-wb-secondary">공동구매를 열려면 먼저 상품을 등록해야 해요.</p>
        </div>
        <Button onClick={() => setShowCreate(true)}>
          <Plus className="h-4 w-4" /> 상품 등록
        </Button>
      </div>

      <input
        type="text"
        value={keyword}
        onChange={(e) => handleKeywordChange(e.target.value)}
        placeholder="상품명으로 검색"
        className="w-full rounded-lg border border-wb-line bg-wb-surface px-3 py-2 text-sm outline-none sm:w-64"
      />

      {error && <Banner tone="error">{error}</Banner>}

      {loading && items === null ? (
        <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : items === null || items.length === 0 ? (
        <EmptyState
          icon={Package}
          title={keyword ? "검색 결과가 없어요" : "아직 등록한 상품이 없어요"}
          message={
            keyword
              ? "다른 검색어로 다시 시도해보세요."
              : "상품 등록 버튼을 눌러 공동구매를 열 상품을 먼저 등록해보세요."
          }
        />
      ) : (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {items.map((product) => (
              <ProductCard
                key={product.id}
                product={product}
                onEdit={() => setEditTarget(product)}
                onDelete={() => setDeleteTarget(product)}
              />
            ))}
          </div>
          <Pagination page={page} totalPages={totalPages} onChange={setPage} />
        </>
      )}

      <ProductCreateModal
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onCreated={() => {
          reload();
          showToast("상품이 등록되었습니다.");
        }}
      />

      <ProductEditModal
        open={editTarget !== null}
        product={editTarget}
        onClose={() => setEditTarget(null)}
        onUpdated={() => {
          reload();
          showToast("상품 정보가 수정되었습니다.");
        }}
      />

      <ProductDeleteModal
        open={deleteTarget !== null}
        product={deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onDeleted={() => {
          reload();
          showToast("상품이 삭제되었습니다.");
        }}
      />
    </div>
  );
}

function ProductCard({
  product,
  onEdit,
  onDelete,
}: {
  product: ProductMineResponse;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const [thumbnailFailed, setThumbnailFailed] = useState(false);
  const catalog = resolveCatalogEntry(product.productName);

  return (
    <div className="overflow-hidden rounded-2xl border border-wb-line bg-wb-surface">
      <div className="relative">
        {product.thumbnailUrl && !thumbnailFailed ? (
          <div className="flex h-36 w-full items-center justify-center bg-wb-canvas">
            {/* eslint-disable-next-line @next/next/no-img-element -- 판매자가 등록한 외부 썸네일 URL이라 next/image 최적화 대상이 아님 */}
            <img
              src={product.thumbnailUrl}
              alt={product.productName}
              className="h-full w-full object-contain"
              onError={() => setThumbnailFailed(true)}
            />
          </div>
        ) : (
          <GroupBuyArtwork entry={catalog} className="h-36 w-full rounded-none" />
        )}
        <div className="absolute left-2.5 top-2.5">
          <StatusPill tone={STATUS_TONE[product.status]}>{STATUS_LABEL[product.status]}</StatusPill>
        </div>
      </div>
      <div className="space-y-2 p-4">
        <p className="text-xs text-wb-secondary">{formatDateTime(product.createdAt)}</p>
        <p className="line-clamp-2 min-h-12 text-base font-bold">{product.productName}</p>
        {product.description && (
          <p className="truncate text-xs text-wb-secondary">{product.description}</p>
        )}
        <p className="text-sm font-bold">{won(product.startPrice)}</p>
        <div className="flex gap-2 pt-1">
          <Button variant="secondary" className="flex-1 text-xs" onClick={onEdit}>
            수정
          </Button>
          <Button className="flex-1 bg-red-600 text-xs hover:bg-red-600/90" onClick={onDelete}>
            삭제
          </Button>
        </div>
      </div>
    </div>
  );
}
