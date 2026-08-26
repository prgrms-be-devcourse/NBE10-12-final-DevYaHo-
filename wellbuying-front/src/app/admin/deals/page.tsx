"use client";

import { useEffect, useState } from "react";
import { PauseCircle, ShoppingBag } from "lucide-react";
import { GroupBuyStatusTag } from "@/components/groupbuy/GroupBuyStatusTag";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { StatusPill, Tag } from "@/components/ui/Tag";
import {
  approveSuspensionRequest,
  listAdminGroupBuys,
  listSuspensionRequests,
  rejectSuspensionRequest,
} from "@/lib/api/admin";
import { ApiError } from "@/lib/api/http";
import type { GroupBuySummaryResponse, GroupBuySuspensionRequestResponse, GroupBuySuspensionStatus } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";

const SUSPENSION_TABS: { status: GroupBuySuspensionStatus; label: string }[] = [
  { status: "PENDING", label: "처리 대기" },
  { status: "APPROVED", label: "승인됨" },
  { status: "REJECTED", label: "반려됨" },
];

const SUSPENSION_STATUS_TONE: Record<GroupBuySuspensionStatus, "orange" | "green" | "red"> = {
  PENDING: "orange",
  APPROVED: "green",
  REJECTED: "red",
};

const SUSPENSION_STATUS_LABEL: Record<GroupBuySuspensionStatus, string> = {
  PENDING: "처리 대기",
  APPROVED: "승인됨",
  REJECTED: "반려됨",
};

function SuspensionRequestsPanel({ status }: { status: GroupBuySuspensionStatus }) {
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<GroupBuySuspensionRequestResponse[] | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [actioningId, setActioningId] = useState<number | null>(null);

  useEffect(() => {
    let ignore = false;

    listSuspensionRequests({ status, page })
      .then((response) => {
        if (ignore) return;
        setItems(response.content);
        setTotalPages(response.page.totalPages);
      })
      .catch((e) => {
        if (ignore) return;
        setItems([]);
        setError(e instanceof ApiError ? e.message : "판매정지 요청 목록을 불러오지 못했어요.");
      });

    return () => {
      ignore = true;
    };
  }, [status, page]);

  async function handleApprove(id: number) {
    setActioningId(id);
    setError(null);
    try {
      await approveSuspensionRequest(id);
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
      await rejectSuspensionRequest(id);
      setItems((prev) => (prev ?? []).filter((item) => item.id !== id));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "반려에 실패했어요.");
    } finally {
      setActioningId(null);
    }
  }

  if (items === null) {
    return <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>;
  }

  return (
    <div className="space-y-4">
      {error && <Banner tone="error">{error}</Banner>}

      {items.length === 0 ? (
        <EmptyState icon={PauseCircle} title="해당 상태의 요청이 없어요" message="다른 탭을 확인해보세요." />
      ) : (
        <div className="space-y-3">
          {items.map((item) => (
            <div
              key={item.id}
              className="flex flex-col gap-4 rounded-2xl border border-wb-line bg-wb-surface p-4 sm:flex-row sm:items-center"
            >
              <div className="min-w-0 flex-1">
                <div className="mb-1 flex items-center gap-2 text-xs text-wb-secondary">
                  <Tag>공동구매 #{item.groupBuyId}</Tag>
                  <span>{formatDateTime(item.requestedAt)}</span>
                </div>
                <p className="text-sm font-bold">{item.groupBuyTitle}</p>
                <p className="text-xs text-wb-secondary">{item.reason ?? "사유 미입력"}</p>
              </div>
              <div className="flex items-center justify-between gap-4 sm:flex-col sm:items-end sm:gap-2">
                <StatusPill tone={SUSPENSION_STATUS_TONE[item.status]}>
                  {SUSPENSION_STATUS_LABEL[item.status]}
                </StatusPill>
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

function GroupBuyListSection() {
  const [items, setItems] = useState<GroupBuySummaryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;

    listAdminGroupBuys({ size: 50 })
      .then((response) => {
        if (!ignore) setItems(response.content);
      })
      .catch((e) => {
        if (ignore) return;
        setItems([]);
        setError(e instanceof ApiError ? e.message : "공동구매 목록을 불러오지 못했어요.");
      });

    return () => {
      ignore = true;
    };
  }, []);

  if (items === null) {
    return <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>;
  }

  return (
    <div className="space-y-3">
      {error && <Banner tone="error">{error}</Banner>}

      {items.length === 0 ? (
        <EmptyState icon={ShoppingBag} title="등록된 공동구매가 없어요" message="아직 개설된 공동구매가 없어요." />
      ) : (
        <div className="overflow-hidden rounded-2xl border border-wb-line bg-wb-surface">
          <div className="grid grid-cols-[2fr_1fr_100px_100px] gap-3 border-b border-wb-line bg-wb-canvas/60 px-5 py-2.5 text-xs font-bold text-wb-secondary">
            <span>공동구매</span>
            <span>진행률</span>
            <span>상태</span>
            <span>판매정지</span>
          </div>
          {items.map((item) => (
            <div
              key={item.id}
              className="grid grid-cols-[2fr_1fr_100px_100px] items-center gap-3 border-b border-wb-line px-5 py-3.5 last:border-0"
            >
              <div className="min-w-0">
                <p className="line-clamp-1 text-sm font-bold">{item.title}</p>
                <p className="line-clamp-1 text-xs text-wb-secondary">{item.productName}</p>
              </div>
              <p className="text-xs font-bold">
                {item.currentQuantity.toLocaleString("ko-KR")} / {item.maxQuantity.toLocaleString("ko-KR")}개
              </p>
              <GroupBuyStatusTag status={item.status} />
              {item.suspended ? <StatusPill tone="red">정지됨</StatusPill> : <span className="text-xs text-wb-secondary">-</span>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function AdminDealsPage() {
  const [suspensionStatus, setSuspensionStatus] = useState<GroupBuySuspensionStatus>("PENDING");

  return (
    <div className="mx-auto max-w-5xl space-y-8 px-6 py-9">
      <div>
        <p className="text-xs font-bold tracking-wide text-wb-green">GROUP BUYING</p>
        <h1 className="mt-1 text-3xl font-bold">공동구매 관리</h1>
        <p className="mt-1 text-sm text-wb-secondary">공동구매 현황과 판매정지 요청을 한 화면에서 확인합니다.</p>
      </div>

      <section className="space-y-4">
        <h2 className="text-lg font-bold">판매정지 요청</h2>
        <div className="flex flex-wrap gap-2">
          {SUSPENSION_TABS.map((tab) => (
            <button
              key={tab.status}
              onClick={() => setSuspensionStatus(tab.status)}
              className={`rounded-full px-4 py-2 text-xs font-bold ${
                suspensionStatus === tab.status
                  ? "bg-wb-green text-white"
                  : "border border-wb-line bg-wb-surface text-wb-secondary"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <SuspensionRequestsPanel key={suspensionStatus} status={suspensionStatus} />
      </section>

      <section className="space-y-4">
        <h2 className="text-lg font-bold">전체 공동구매 목록</h2>
        <GroupBuyListSection />
      </section>
    </div>
  );
}
