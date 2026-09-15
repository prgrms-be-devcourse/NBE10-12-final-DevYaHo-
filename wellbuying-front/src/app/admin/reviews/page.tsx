"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { PackageSearch } from "lucide-react";
import { ActionLogPanel } from "@/components/admin/ActionLogPanel";
import { ActionReasonModal } from "@/components/admin/ActionReasonModal";
import { GroupBuyArtwork } from "@/components/deal/GroupBuyArtwork";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { Pagination } from "@/components/ui/Pagination";
import { StatusPill, Tag } from "@/components/ui/Tag";
import {
  approveProduct,
  deregisterProduct,
  listAdminProducts,
  listProductActionLogs,
  rejectProduct,
} from "@/lib/api/admin";
import type { PageResponse, ProductAdminResponse, ProductStatus } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { resolveCatalogEntry } from "@/lib/groupBuy/seedCatalog";
import { showToast } from "@/lib/toast/toastStore";
import { invalidatePagedQuery, usePagedQuery } from "@/hooks/usePagedQuery";

type PendingAction = { id: number; kind: "approve" | "reject" };

const ACTION_MODAL_CONFIG: Record<
  PendingAction["kind"],
  { title: string; actionLabel: string; confirmVariant: "primary" | "secondary" }
> = {
  approve: { title: "상품 승인", actionLabel: "승인", confirmVariant: "primary" },
  reject: { title: "상품 반려", actionLabel: "반려", confirmVariant: "secondary" },
};

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
  const [reloadToken, setReloadToken] = useState(0);
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);

  const { data, error, loading } = usePagedQuery<PageResponse<ProductAdminResponse>>(
    "admin-product-review",
    { status, page, reloadToken },
    () => listAdminProducts({ status, page, size: 10 }),
    "상품 목록을 불러오지 못했어요.",
  );
  const items = data?.content ?? null;
  const totalPages = data?.page.totalPages ?? 0;

  const ACTION_FN: Record<PendingAction["kind"], (id: number, reason: string) => Promise<void>> = {
    approve: approveProduct,
    reject: rejectProduct,
  };

  async function handleConfirmAction(reason: string) {
    if (!pendingAction) return;
    const { actionLabel } = ACTION_MODAL_CONFIG[pendingAction.kind];
    await ACTION_FN[pendingAction.kind](pendingAction.id, reason);
    invalidatePagedQuery("admin-product-review");
    setReloadToken((t) => t + 1);
    setPendingAction(null);
    showToast(`"${reason}" 사유로 ${actionLabel} 처리되었습니다.`);
  }

  if (loading && items === null) {
    return <p className="py-24 text-center text-sm text-wb-secondary">불러오는 중...</p>;
  }

  return (
    <div className="space-y-4">
      {error && <Banner tone="error">{error}</Banner>}

      {items === null || items.length === 0 ? (
        <EmptyState icon={PackageSearch} title="해당 상태의 상품이 없어요" message="다른 필터를 확인해보세요." />
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {items.map((item) => (
            <ProductAdminCard key={item.id} item={item}>
              {item.status === "PENDING" && (
                <div className="flex gap-2 pt-1">
                  <Button
                    variant="secondary"
                    className="flex-1 text-xs"
                    onClick={() => setPendingAction({ id: item.id, kind: "reject" })}
                  >
                    반려
                  </Button>
                  <Button className="flex-1 text-xs" onClick={() => setPendingAction({ id: item.id, kind: "approve" })}>
                    승인
                  </Button>
                </div>
              )}
            </ProductAdminCard>
          ))}
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />

      <ActionReasonModal
        open={pendingAction !== null}
        title={pendingAction ? ACTION_MODAL_CONFIG[pendingAction.kind].title : ""}
        actionLabel={pendingAction ? ACTION_MODAL_CONFIG[pendingAction.kind].actionLabel : ""}
        confirmVariant={pendingAction ? ACTION_MODAL_CONFIG[pendingAction.kind].confirmVariant : "primary"}
        onClose={() => setPendingAction(null)}
        onConfirm={handleConfirmAction}
      />
    </div>
  );
}

// ProductStatus엔 ALL이 없어 상태별 API를 병렬 호출해 합친다 - 카탈로그 규모상 상태당 100개면 충분하다고 보고
// 페이지네이션 대신 상품명 검색만 제공한다. 검토 대기(PENDING)는 "등록 심사" 탭에서 다루므로 여기서는 제외한다.
// 승인된 상품만 보여준다 - 검토대기/반려 상품은 "등록 심사" 탭에서 다룬다
// 승인된 상품 목록 - 상품 삭제 관리(구 별도 탭)의 강제 삭제 기능을 여기로 흡수했다.
// 등록 해지는 물리적 삭제 없이 반려(REJECTED)와 동일하게 상태만 전환하며, 사유 입력만 받는다.
function AllProductsPanel() {
  const [page, setPage] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [reloadToken, setReloadToken] = useState(0);
  const [deregisterTargetId, setDeregisterTargetId] = useState<number | null>(null);
  const [deregisterTargetName, setDeregisterTargetName] = useState("");
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

  const { data, error, loading } = usePagedQuery<PageResponse<ProductAdminResponse>>(
    "admin-all-products",
    { keyword: debouncedKeyword, page, reloadToken },
    () => listAdminProducts({ status: "APPROVED", keyword: debouncedKeyword || undefined, page, size: 10 }),
    "상품 목록을 불러오지 못했어요.",
  );
  const items = data?.content ?? null;
  const totalPages = data?.page.totalPages ?? 0;

  function startDeregister(id: number, name: string) {
    setDeregisterTargetId(id);
    setDeregisterTargetName(name);
  }

  async function handleConfirmDeregister(reason: string) {
    if (deregisterTargetId === null) return;
    await deregisterProduct(deregisterTargetId, reason);
    invalidatePagedQuery("admin-all-products");
    invalidatePagedQuery("admin-product-action-logs");
    setReloadToken((t) => t + 1);
    setDeregisterTargetId(null);
    showToast("정상적으로 등록 해지되었습니다.");
  }

  return (
    <div className="space-y-4">
      {error && <Banner tone="error">{error}</Banner>}

      <input
        value={keyword}
        onChange={(e) => handleKeywordChange(e.target.value)}
        placeholder="상품명 검색"
        className="w-full rounded-lg border border-wb-line bg-wb-surface px-3 py-2 text-sm outline-none sm:w-64"
      />

      {loading && items === null ? (
        <p className="py-24 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : items === null || items.length === 0 ? (
        <EmptyState icon={PackageSearch} title="등록된 상품이 없어요" message="검색어를 확인해보세요." />
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {items.map((item) => (
            <ProductAdminCard key={item.id} item={item}>
              <Button
                className="w-full bg-red-600 text-xs hover:bg-red-600/90"
                onClick={() => startDeregister(item.id, item.productName)}
              >
                등록 해지
              </Button>
            </ProductAdminCard>
          ))}
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />

      <ActionReasonModal
        open={deregisterTargetId !== null}
        title={`등록 해지 - ${deregisterTargetName}`}
        actionLabel="등록 해지"
        confirmVariant="secondary"
        onClose={() => setDeregisterTargetId(null)}
        onConfirm={handleConfirmDeregister}
      />
    </div>
  );
}

// 판매자 상품관리(producer/products) 카드와 동일한 패턴 - 썸네일 + 상품명 + 가격, 하단 액션만 패널별로 다르다
function ProductAdminCard({ item, children }: { item: ProductAdminResponse; children?: ReactNode }) {
  const [thumbnailFailed, setThumbnailFailed] = useState(false);
  const catalog = resolveCatalogEntry(item.productName);

  return (
    <div className="overflow-hidden rounded-2xl border border-wb-line bg-wb-surface">
      <div className="relative">
        {item.thumbnailUrl && !thumbnailFailed ? (
          <div className="flex h-36 w-full items-center justify-center bg-wb-canvas">
            {/* eslint-disable-next-line @next/next/no-img-element -- 판매자가 등록한 외부 썸네일 URL이라 next/image 최적화 대상이 아님 */}
            <img
              src={item.thumbnailUrl}
              alt={item.productName}
              className="h-full w-full object-contain"
              onError={() => setThumbnailFailed(true)}
            />
          </div>
        ) : (
          <GroupBuyArtwork entry={catalog} className="h-36 w-full rounded-none" />
        )}
        <div className="absolute left-2.5 top-2.5">
          <StatusPill tone={STATUS_TONE[item.status]}>{STATUS_LABEL[item.status]}</StatusPill>
        </div>
      </div>
      <div className="space-y-2 p-4">
        <div className="flex items-center gap-2 text-xs text-wb-secondary">
          <Tag>{item.sellerEmail}</Tag>
          <span>{formatDateTime(item.createdAt)}</span>
        </div>
        <p className="line-clamp-2 min-h-12 text-base font-bold">{item.productName}</p>
        <p className="text-sm font-bold">{item.startPrice.toLocaleString("ko-KR")}원</p>
        {children}
      </div>
    </div>
  );
}

const VIEW_TABS: { key: "review" | "all" | "history"; label: string }[] = [
  { key: "all", label: "전체 상품목록" },
  { key: "review", label: "등록 심사" },
  { key: "history", label: "처리 이력" },
];

export default function AdminReviewsPage() {
  const [view, setView] = useState<"review" | "all" | "history">("all");
  const [status, setStatus] = useState<ProductStatus>("PENDING");

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div>
        <p className="text-xs font-bold tracking-wide text-wb-green">PRODUCTS</p>
        <h1 className="mt-1 text-3xl font-bold">상품 관리</h1>
        <p className="mt-1 text-sm text-wb-secondary">등록된 상품을 심사하고 전체 현황을 확인합니다.</p>
      </div>

      <div className="flex gap-4 border-b border-wb-line">
        {VIEW_TABS.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setView(tab.key)}
            className={`-mb-px border-b-2 px-1 pb-3 text-sm font-bold ${
              view === tab.key ? "border-wb-green text-wb-green" : "border-transparent text-wb-secondary"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {view === "review" ? (
        <>
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
        </>
      ) : view === "all" ? (
        <AllProductsPanel />
      ) : (
        <ActionLogPanel
          cacheNamespace="admin-product-action-logs"
          fetcher={listProductActionLogs}
          targetLabelHeader="상품명"
          emptyMessage="아직 승인/반려 처리된 상품이 없어요."
        />
      )}
    </div>
  );
}
