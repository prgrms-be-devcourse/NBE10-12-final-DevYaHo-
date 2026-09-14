"use client";

import { useState } from "react";
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
// 각 도메인의 action-logs 조회 API만 fetcher로 넘겨 재사용한다.
export function ActionLogPanel({
  cacheNamespace,
  fetcher,
  targetLabelHeader,
  emptyMessage,
}: {
  cacheNamespace: string;
  fetcher: (params: { page: number; size?: number }) => Promise<PageResponse<AdminActionLogResponse>>;
  targetLabelHeader: string;
  emptyMessage: string;
}) {
  const [page, setPage] = useState(0);

  const { data, error, loading } = usePagedQuery<PageResponse<AdminActionLogResponse>>(
    cacheNamespace,
    { page },
    () => fetcher({ page }),
    "처리 이력을 불러오지 못했어요.",
  );
  const items = data?.content ?? null;
  const totalPages = data?.page.totalPages ?? 0;

  return (
    <div className="space-y-4">
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
