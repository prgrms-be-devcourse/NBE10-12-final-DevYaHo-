"use client";

import { useState } from "react";
import { PackageSearch } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { StatusPill, Tag } from "@/components/ui/Tag";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { REVIEW_STATUS_LABEL, won, type ReviewStatus } from "@/lib/mock/types";

const TONE: Record<ReviewStatus, "orange" | "green" | "red"> = {
  pending: "orange",
  approved: "green",
  rejected: "red",
};

export default function AdminReviewsPage() {
  const { submissions, reviewStatuses, setReview } = useDemoStore();
  const [filter, setFilter] = useState<ReviewStatus | null>("pending");

  const filtered = filter ? submissions.filter((item) => reviewStatuses[item.id] === filter) : submissions;

  function count(status: ReviewStatus) {
    return Object.values(reviewStatuses).filter((s) => s === status).length;
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div>
        <p className="text-xs font-bold tracking-wide text-wb-green">PRODUCT REVIEW</p>
        <h1 className="mt-1 text-3xl font-bold">상품 심사</h1>
        <p className="mt-1 text-sm text-wb-secondary">생산 정보와 가격 구조 공개 여부를 확인합니다.</p>
      </div>

      <div className="flex flex-wrap gap-2">
        <FilterButton label="전체" count={submissions.length} active={filter === null} onClick={() => setFilter(null)} />
        <FilterButton label="검토 대기" count={count("pending")} active={filter === "pending"} onClick={() => setFilter("pending")} />
        <FilterButton label="승인" count={count("approved")} active={filter === "approved"} onClick={() => setFilter("approved")} />
        <FilterButton label="반려" count={count("rejected")} active={filter === "rejected"} onClick={() => setFilter("rejected")} />
      </div>

      {filtered.length === 0 ? (
        <EmptyState icon={PackageSearch} title="해당 상태의 상품이 없어요" message="다른 필터를 확인해보세요." />
      ) : (
        <div className="space-y-3">
          {filtered.map((item) => {
            const status = reviewStatuses[item.id] ?? "pending";
            return (
              <div key={item.id} className="flex flex-col gap-4 rounded-2xl border border-wb-line bg-wb-surface p-4 sm:flex-row sm:items-center">
                <div className="flex min-w-0 flex-1 items-center gap-4">
                  <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-xl bg-wb-light-green/50">
                    <PackageSearch className="h-6 w-6 text-wb-green" />
                  </div>
                  <div className="min-w-0">
                    <div className="mb-1 flex items-center gap-2 text-xs text-wb-secondary">
                      <Tag>{item.category}</Tag>
                      <span>{item.submittedAt}</span>
                    </div>
                    <p className="line-clamp-2 text-sm font-bold">{item.title}</p>
                    <p className="text-xs text-wb-secondary">{item.producer}</p>
                  </div>
                </div>
                <div className="flex items-center justify-between gap-4 sm:flex-col sm:items-end sm:gap-2">
                  <div className="text-right">
                    <p className="text-xs text-wb-secondary">제안 시작가</p>
                    <p className="text-sm font-bold">{won(item.proposedPrice)}</p>
                  </div>
                  <StatusPill tone={TONE[status]}>{REVIEW_STATUS_LABEL[status]}</StatusPill>
                </div>
                <div className="flex gap-2">
                  {status === "pending" ? (
                    <>
                      <Button variant="secondary" onClick={() => setReview(item.id, "rejected")}>
                        반려
                      </Button>
                      <Button onClick={() => setReview(item.id, "approved")}>승인</Button>
                    </>
                  ) : (
                    <Button variant="secondary" onClick={() => setReview(item.id, "pending")}>
                      재검토
                    </Button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function FilterButton({
  label,
  count,
  active,
  onClick,
}: {
  label: string;
  count: number;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`rounded-full px-4 py-2 text-xs font-bold ${
        active ? "bg-wb-green text-white" : "border border-wb-line bg-wb-surface text-wb-secondary"
      }`}
    >
      {label} {count}
    </button>
  );
}
