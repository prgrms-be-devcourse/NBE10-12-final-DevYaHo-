"use client";

import { useEffect, useState } from "react";
import { Store } from "lucide-react";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { StatusPill, Tag } from "@/components/ui/Tag";
import { approveSeller, listSellerApplications, rejectSeller } from "@/lib/api/admin";
import { ApiError } from "@/lib/api/http";
import type { SellerInfoResponse, SellerStatus } from "@/lib/api/types";

const TABS: { status: SellerStatus; label: string }[] = [
  { status: "PENDING", label: "승인 대기" },
  { status: "ACTIVE", label: "승인됨" },
  { status: "TERMINATED", label: "거절됨" },
];

const STATUS_TONE: Record<SellerStatus, "orange" | "green" | "red" | "neutral"> = {
  PENDING: "orange",
  ACTIVE: "green",
  TERMINATED: "red",
  SUSPENDED: "neutral",
};

const STATUS_LABEL: Record<SellerStatus, string> = {
  PENDING: "승인 대기",
  ACTIVE: "승인됨",
  SUSPENDED: "정지됨",
  TERMINATED: "거절됨",
};

function SellerApplicationsPanel({ status }: { status: SellerStatus }) {
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<SellerInfoResponse[] | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [actioningId, setActioningId] = useState<number | null>(null);

  useEffect(() => {
    listSellerApplications({ status, page })
      .then((response) => {
        setItems(response.content);
        setTotalPages(response.page.totalPages);
      })
      .catch((e) => {
        setItems([]);
        setError(e instanceof ApiError ? e.message : "판매자 신청 목록을 불러오지 못했어요.");
      });
  }, [status, page]);

  async function handleApprove(id: number) {
    setActioningId(id);
    setError(null);
    try {
      await approveSeller(id);
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
      await rejectSeller(id);
      setItems((prev) => (prev ?? []).filter((item) => item.id !== id));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "거절에 실패했어요.");
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
        <EmptyState icon={Store} title="해당 상태의 신청이 없어요" message="다른 탭을 확인해보세요." />
      ) : (
        <div className="space-y-3">
          {items.map((item) => (
            <div
              key={item.id}
              className="flex flex-col gap-4 rounded-2xl border border-wb-line bg-wb-surface p-4 sm:flex-row sm:items-center"
            >
              <div className="min-w-0 flex-1">
                <div className="mb-1 flex items-center gap-2 text-xs text-wb-secondary">
                  <Tag>회원 #{item.memberId}</Tag>
                  <span>{item.createdAt}</span>
                </div>
                <p className="text-sm font-bold">{item.companyName ?? "상호명 미입력"}</p>
                <p className="text-xs text-wb-secondary">{item.bankName}</p>
              </div>
              <div className="flex items-center justify-between gap-4 sm:flex-col sm:items-end sm:gap-2">
                <StatusPill tone={STATUS_TONE[item.status]}>{STATUS_LABEL[item.status]}</StatusPill>
                {item.status === "PENDING" && (
                  <div className="flex gap-2">
                    <Button
                      variant="secondary"
                      className="px-3 py-1.5 text-xs"
                      loading={actioningId === item.id}
                      onClick={() => handleReject(item.id)}
                    >
                      거절
                    </Button>
                    <Button
                      className="px-3 py-1.5 text-xs"
                      loading={actioningId === item.id}
                      onClick={() => handleApprove(item.id)}
                    >
                      승인
                    </Button>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex justify-center gap-2">
          <Button
            variant="secondary"
            className="px-3 py-1.5 text-xs"
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
          >
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

export default function AdminSellersPage() {
  const [status, setStatus] = useState<SellerStatus>("PENDING");

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div>
        <p className="text-xs font-bold tracking-wide text-wb-green">SELLER APPLICATIONS</p>
        <h1 className="mt-1 text-3xl font-bold">판매자 승인</h1>
        <p className="mt-1 text-sm text-wb-secondary">판매자 전환 신청을 검토하고 승인/거절합니다.</p>
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

      <SellerApplicationsPanel key={status} status={status} />
    </div>
  );
}
