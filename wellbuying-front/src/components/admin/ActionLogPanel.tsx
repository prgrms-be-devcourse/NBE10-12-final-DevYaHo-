"use client";

import { useEffect, useRef, useState } from "react";
import { History } from "lucide-react";
import { Banner } from "@/components/ui/Banner";
import { EmptyState } from "@/components/ui/EmptyState";
import { Pagination } from "@/components/ui/Pagination";
import { StatusPill } from "@/components/ui/Tag";
import type { AdminActionLogResponse, AdminActionType, PageResponse } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { usePagedQuery } from "@/hooks/usePagedQuery";

const ACTION_LABEL: Record<AdminActionType, string> = {
  APPROVE: "승인",
  REJECT: "반려",
  SUSPEND: "정지",
  REACTIVATE: "정지 복귀",
};

const ACTION_TONE: Record<AdminActionType, "green" | "red" | "orange"> = {
  APPROVE: "green",
  REJECT: "red",
  SUSPEND: "red",
  REACTIVATE: "orange",
};

// 상품/공동구매 판매정지/판매자 승인·정지 등 관리자 처리 이력을 보여주는 공용 패널.
// 각 도메인의 action-logs 조회 API만 fetcher로 넘겨 재사용한다. searchPlaceholder를 주면
// 제목 검색창을 같이 보여준다(해당 fetcher가 keyword 파라미터를 지원하는 경우에만 사용할 것).
export function ActionLogPanel({
  cacheNamespace,
  fetcher,
  targetLabelHeader,
  emptyMessage,
  searchPlaceholder,
}: {
  cacheNamespace: string;
  fetcher: (params: { page: number; size?: number; keyword?: string }) => Promise<PageResponse<AdminActionLogResponse>>;
  targetLabelHeader: string;
  emptyMessage: string;
  searchPlaceholder?: string;
}) {
  const [page, setPage] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
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

  const { data, error, loading } = usePagedQuery<PageResponse<AdminActionLogResponse>>(
    cacheNamespace,
    { page, keyword: debouncedKeyword },
    () => fetcher({ page, size: 10, keyword: debouncedKeyword || undefined }),
    "처리 이력을 불러오지 못했어요.",
  );
  const items = data?.content ?? null;
  const totalPages = data?.page.totalPages ?? 0;

  return (
    <div className="space-y-4">
      {searchPlaceholder && (
        <input
          value={keyword}
          onChange={(e) => handleKeywordChange(e.target.value)}
          placeholder={searchPlaceholder}
          className="w-full rounded-lg border border-wb-line bg-white px-4 py-2.5 text-sm outline-none focus:border-wb-green sm:w-64"
        />
      )}

      {error && <Banner tone="error">{error}</Banner>}

      {loading && items === null ? (
        <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : items === null || items.length === 0 ? (
        <EmptyState icon={History} title="처리 이력이 없어요" message={emptyMessage} />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-wb-line">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-wb-line bg-wb-canvas text-left text-xs font-bold text-wb-secondary">
                <th className="px-4 py-3">{targetLabelHeader}</th>
                <th className="px-4 py-3">처리</th>
                <th className="px-4 py-3">사유</th>
                <th className="px-4 py-3">처리자</th>
                <th className="px-4 py-3">처리 일시</th>
              </tr>
            </thead>
            <tbody>
              {items.map((log) => (
                <tr key={log.id} className="border-b border-wb-line last:border-0 hover:bg-wb-canvas/50">
                  <td className="px-4 py-3 font-medium">{log.targetLabel}</td>
                  <td className="px-4 py-3">
                    <StatusPill tone={ACTION_TONE[log.action]}>{ACTION_LABEL[log.action]}</StatusPill>
                  </td>
                  <td className="max-w-xs px-4 py-3">
                    <p className="truncate text-wb-secondary" title={log.reason}>
                      {log.reason}
                    </p>
                  </td>
                  <td className="px-4 py-3 text-wb-secondary">{log.adminName}</td>
                  <td className="px-4 py-3 text-wb-secondary">{formatDateTime(log.occurredAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />
    </div>
  );
}
