"use client";

import { useEffect, useState } from "react";
import { PackageSearch } from "lucide-react";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { StatusPill, Tag } from "@/components/ui/Tag";
import { approveProduct, listAdminProducts, rejectProduct } from "@/lib/api/admin";
import { ApiError } from "@/lib/api/http";
import type { ProductAdminResponse, ProductStatus } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";

const TABS: { status: ProductStatus; label: string }[] = [
  { status: "PENDING", label: "검토 대기" },
  { status: "APPROVED", label: "승인" },
  { status: "REJECTED", label: "반려" },
];

const STATUS_TONE: Record<ProductStatus, "orange" | "green" | "red"> = {
  PENDING: "orange",
  APPROVED: "green",
  REJECTED: "red",
};

const STATUS_LABEL: Record<ProductStatus, string> = {
  PENDING: "검토 대기",
  APPROVED: "승인",
  REJECTED: "반려",
};

function ProductReviewPanel({ status }: { status: ProductStatus }) {
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<ProductAdminResponse[] | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [actioningId, setActioningId] = useState<number | null>(null);

  useEffect(() => {
    let ignore = false;

    listAdminProducts({ status, page })
      .then((response) => {
        if (ignore) return;
        setItems(response.content);
        setTotalPages(response.page.totalPages);
      })
      .catch((e) => {
        if (ignore) return;
        setItems([]);
        setError(e instanceof ApiError ? e.message : "상품 목록을 불러오지 못했어요.");
      });

    return () => {
      ignore = true;
    };
  }, [status, page]);

  async function handleApprove(id: number) {
    setActioningId(id);
    setError(null);
    try {
      await approveProduct(id);
      setItems((prev) => (prev ?? []).filter((item) => item.id !== id));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "승인에 실패했어요.");
    } finally {
      setActioningId(null);
    }
  }

  async function handleReject(id: number) {
    setActioningId(id);
    setError(null);
    try {
      await rejectProduct(id);
      setItems((prev) => (prev ?? []).filter((item) => item.id !== id));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "반려에 실패했어요.");
    } finally {
      setActioningId(null);
    }
  }

  if (items === null) {
    return <p className="py-24 text-center text-sm text-wb-secondary">불러오는 중...</p>;
  }

  return (
    <div className="space-y-4">
      {error && <Banner tone="error">{error}</Banner>}

      {items.length === 0 ? (
        <EmptyState icon={PackageSearch} title="해당 상태의 상품이 없어요" message="다른 필터를 확인해보세요." />
      ) : (
        <div className="space-y-3">
          {items.map((item) => (
            <div key={item.id} className="flex flex-col gap-4 rounded-2xl border border-wb-line bg-wb-surface p-4 sm:flex-row sm:items-center">
              <div className="flex min-w-0 flex-1 items-center gap-4">
                <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-xl bg-wb-light-green/50">
                  <PackageSearch className="h-6 w-6 text-wb-green" />
                </div>
                <div className="min-w-0">
                  <div className="mb-1 flex items-center gap-2 text-xs text-wb-secondary">
                    <Tag>판매자 #{item.sellerId}</Tag>
                    <span>{formatDateTime(item.createdAt)}</span>
                  </div>
                  <p className="line-clamp-2 text-sm font-bold">{item.productName}</p>
                </div>
              </div>
              <div className="flex items-center justify-between gap-4 sm:flex-col sm:items-end sm:gap-2">
                <div className="text-right">
                  <p className="text-xs text-wb-secondary">제안 시작가</p>
                  <p className="text-sm font-bold">{item.startPrice.toLocaleString("ko-KR")}원</p>
                </div>
                <StatusPill tone={STATUS_TONE[item.status]}>{STATUS_LABEL[item.status]}</StatusPill>
              </div>
              {item.status === "PENDING" && (
                <div className="flex gap-2">
                  <Button
                    variant="secondary"
                    className="px-3 py-1.5 text-xs"
                    loading={actioningId === item.id}
                    onClick={() => handleReject(item.id)}
                  >
                    반려
                  </Button>
                  <Button className="px-3 py-1.5 text-xs" loading={actioningId === item.id} onClick={() => handleApprove(item.id)}>
                    승인
                  </Button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex justify-center gap-2">
          <Button variant="secondary" className="px-3 py-1.5 text-xs" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            이전
          </Button>
          <span className="flex items-center px-2 text-xs text-wb-secondary">
            {page + 1} / {totalPages}
          </span>
          <Button
            variant="secondary"
            className="px-3 py-1.5 text-xs"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            다음
          </Button>
        </div>
      )}
    </div>
  );
}

export default function AdminReviewsPage() {
  const [status, setStatus] = useState<ProductStatus>("PENDING");

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div>
        <p className="text-xs font-bold tracking-wide text-wb-green">PRODUCT REVIEW</p>
        <h1 className="mt-1 text-3xl font-bold">상품 심사</h1>
        <p className="mt-1 text-sm text-wb-secondary">등록된 상품의 판매 승인 여부를 확인합니다.</p>
      </div>

      <div className="flex flex-wrap gap-2">
        {TABS.map((tab) => (
          <button
            key={tab.status}
            onClick={() => setStatus(tab.status)}
            className={`rounded-full px-4 py-2 text-xs font-bold ${
              status === tab.status ? "bg-wb-green text-white" : "border border-wb-line bg-wb-surface text-wb-secondary"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <ProductReviewPanel key={status} status={status} />
    </div>
  );
}
