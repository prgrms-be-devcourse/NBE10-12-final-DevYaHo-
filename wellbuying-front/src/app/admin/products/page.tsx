"use client";

import { useEffect, useRef, useState } from "react";
import { Trash2 } from "lucide-react";
import { ActionReasonModal } from "@/components/admin/ActionReasonModal";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { forceDeleteProduct, listAdminProducts, listDeletedProducts } from "@/lib/api/admin";
import { ApiError } from "@/lib/api/http";
import type { ProductAdminResponse, ProductDeletedAdminResponse } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";

type Tab = "force-delete" | "deleted-history";

const TABS: { key: Tab; label: string }[] = [
  { key: "force-delete", label: "강제 삭제" },
  { key: "deleted-history", label: "삭제 이력" },
];

export default function AdminProductsPage() {
  const [tab, setTab] = useState<Tab>("force-delete");

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-6 py-9">
      <div className="flex items-center gap-2.5">
        <Trash2 className="h-6 w-6 text-wb-green" />
        <div>
          <h1 className="text-3xl font-bold">상품 삭제 관리</h1>
          <p className="mt-1 text-sm text-wb-secondary">승인된 상품을 강제 삭제하거나 삭제 이력을 조회해요.</p>
        </div>
      </div>

      <div className="flex gap-1 border-b border-wb-line">
        {TABS.map(({ key, label }) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`px-4 py-2.5 text-sm font-bold transition-colors ${
              tab === key
                ? "border-b-2 border-wb-green text-wb-green"
                : "text-wb-secondary hover:text-wb-ink"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === "force-delete" && <ForceDeletePanel />}
      {tab === "deleted-history" && <DeletedHistoryPanel />}
    </div>
  );
}

function ForceDeletePanel() {
  const [page, setPage] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [items, setItems] = useState<ProductAdminResponse[] | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [targetId, setTargetId] = useState<number | null>(null);
  const [targetName, setTargetName] = useState("");
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
    let ignore = false;
    setError(null);
    setItems(null);
    listAdminProducts({ status: "APPROVED", keyword: debouncedKeyword || undefined, page })
      .then((res) => {
        if (ignore) return;
        setItems(res.content);
        setTotalPages(res.page.totalPages);
      })
      .catch((e) => {
        if (ignore) return;
        setError(e instanceof ApiError ? e.message : "목록을 불러오지 못했어요.");
        setItems([]);
      });
    return () => {
      ignore = true;
    };
  }, [debouncedKeyword, page]);

  function reload() {
    setItems(null);
    setError(null);
    listAdminProducts({ status: "APPROVED", keyword: debouncedKeyword || undefined, page })
      .then((res) => {
        setItems(res.content);
        setTotalPages(res.page.totalPages);
      })
      .catch((e) => {
        setError(e instanceof ApiError ? e.message : "목록을 불러오지 못했어요.");
        setItems([]);
      });
  }

  return (
    <div className="space-y-4">
      <input
        type="text"
        value={keyword}
        onChange={(e) => handleKeywordChange(e.target.value)}
        placeholder="상품명으로 검색"
        className="w-full rounded-lg border border-wb-line bg-white px-4 py-2.5 text-sm outline-none focus:border-wb-green"
      />
      {error && <Banner tone="error">{error}</Banner>}

      {items === null ? (
        <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : items.length === 0 ? (
        <EmptyState icon={Trash2} title="강제 삭제할 상품이 없어요" message="승인된 상품이 없어요." />
      ) : (
        <>
          <div className="overflow-x-auto rounded-xl border border-wb-line">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-wb-line bg-wb-canvas text-left text-xs font-bold text-wb-secondary">
                  <th className="px-4 py-3">ID</th>
                  <th className="px-4 py-3">상품명</th>
                  <th className="px-4 py-3">판매자 ID</th>
                  <th className="px-4 py-3">시작가</th>
                  <th className="px-4 py-3">등록일</th>
                  <th className="px-4 py-3"></th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id} className="border-b border-wb-line last:border-0 hover:bg-wb-canvas/50">
                    <td className="px-4 py-3 text-wb-secondary">{item.id}</td>
                    <td className="px-4 py-3 font-medium">{item.productName}</td>
                    <td className="px-4 py-3 text-wb-secondary">{item.sellerId}</td>
                    <td className="px-4 py-3">{item.startPrice.toLocaleString("ko-KR")}원</td>
                    <td className="px-4 py-3 text-wb-secondary">{formatDateTime(item.createdAt)}</td>
                    <td className="px-4 py-3">
                      <Button
                        className="bg-red-600 text-xs hover:bg-red-600/90"
                        onClick={() => {
                          setTargetId(item.id);
                          setTargetName(item.productName);
                        }}
                      >
                        강제 삭제
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="flex justify-center gap-2">
              <Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                이전
              </Button>
              <span className="flex items-center px-3 text-sm text-wb-secondary">
                {page + 1} / {totalPages}
              </span>
              <Button variant="secondary" disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}>
                다음
              </Button>
            </div>
          )}
        </>
      )}

      <ActionReasonModal
        open={targetId !== null}
        title={`강제 삭제 - ${targetName}`}
        actionLabel="강제 삭제"
        confirmVariant="secondary"
        onClose={() => setTargetId(null)}
        onConfirm={async (reason) => {
          if (targetId === null) return;
          await forceDeleteProduct(targetId, reason);
          setTargetId(null);
          reload();
        }}
      />
    </div>
  );
}

function DeletedHistoryPanel() {
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<ProductDeletedAdminResponse[] | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    setError(null);
    listDeletedProducts({ page })
      .then((res) => {
        if (ignore) return;
        setItems(res.content);
        setTotalPages(res.page.totalPages);
      })
      .catch((e) => {
        if (ignore) return;
        setError(e instanceof ApiError ? e.message : "삭제 이력을 불러오지 못했어요.");
        setItems([]);
      });
    return () => {
      ignore = true;
    };
  }, [page]);

  return (
    <div className="space-y-4">
      {error && <Banner tone="error">{error}</Banner>}

      {items === null ? (
        <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : items.length === 0 ? (
        <EmptyState icon={Trash2} title="삭제 이력이 없어요" message="삭제된 상품이 없어요." />
      ) : (
        <>
          <div className="overflow-x-auto rounded-xl border border-wb-line">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-wb-line bg-wb-canvas text-left text-xs font-bold text-wb-secondary">
                  <th className="px-4 py-3">ID</th>
                  <th className="px-4 py-3">상품명</th>
                  <th className="px-4 py-3">판매자 ID</th>
                  <th className="px-4 py-3">삭제자 ID</th>
                  <th className="px-4 py-3">삭제 사유</th>
                  <th className="px-4 py-3">삭제 시각</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id} className="border-b border-wb-line last:border-0 hover:bg-wb-canvas/50">
                    <td className="px-4 py-3 text-wb-secondary">{item.id}</td>
                    <td className="px-4 py-3 font-medium">{item.productName}</td>
                    <td className="px-4 py-3 text-wb-secondary">{item.sellerId}</td>
                    <td className="px-4 py-3 text-wb-secondary">{item.deletedBy}</td>
                    <td className="max-w-xs px-4 py-3">
                      <p className="truncate text-wb-secondary" title={item.deleteReason}>
                        {item.deleteReason}
                      </p>
                    </td>
                    <td className="px-4 py-3 text-wb-secondary">{formatDateTime(item.deletedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="flex justify-center gap-2">
              <Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                이전
              </Button>
              <span className="flex items-center px-3 text-sm text-wb-secondary">
                {page + 1} / {totalPages}
              </span>
              <Button variant="secondary" disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}>
                다음
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
