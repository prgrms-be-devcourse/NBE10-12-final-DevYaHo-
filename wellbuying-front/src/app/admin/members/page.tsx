"use client";

import { useEffect, useState } from "react";
import { Search, Users } from "lucide-react";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { StatusPill } from "@/components/ui/Tag";
import { listMembers } from "@/lib/api/admin";
import { ApiError } from "@/lib/api/http";
import type { MemberStatus, MemberSummaryResponse } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";

const TABS: { status: MemberStatus | null; label: string }[] = [
  { status: null, label: "전체" },
  { status: "ACTIVE", label: "활성" },
  { status: "DORMANT", label: "휴면" },
  { status: "WITHDRAWN", label: "탈퇴" },
];

const STATUS_LABEL: Record<MemberStatus, string> = {
  ACTIVE: "정상",
  DORMANT: "휴면",
  WITHDRAWN: "탈퇴",
};

const STATUS_TONE: Record<MemberStatus, "green" | "orange" | "neutral"> = {
  ACTIVE: "green",
  DORMANT: "orange",
  WITHDRAWN: "neutral",
};

export default function AdminMembersPage() {
  const [status, setStatus] = useState<MemberStatus | null>(null);
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<MemberSummaryResponse[] | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [activeCount, setActiveCount] = useState(0);
  const [sellerCount, setSellerCount] = useState(0);
  const [query, setQuery] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;

    listMembers({ status: status ?? undefined, page })
      .then((response) => {
        if (ignore) return;
        setItems(response.content);
        setTotalElements(response.page.totalElements);
        setTotalPages(response.page.totalPages);
      })
      .catch((e) => {
        if (ignore) return;
        setItems([]);
        setError(e instanceof ApiError ? e.message : "회원 목록을 불러오지 못했어요.");
      });

    return () => {
      ignore = true;
    };
  }, [status, page]);

  useEffect(() => {
    let ignore = false;

    Promise.all([
      listMembers({ status: "ACTIVE", size: 1 }),
      listMembers({ role: "SELLER", size: 1 }),
    ])
      .then(([active, sellers]) => {
        if (ignore) return;
        setActiveCount(active.page.totalElements);
        setSellerCount(sellers.page.totalElements);
      })
      .catch(() => {
        // 요약 카드는 부가 정보이므로 실패해도 목록 조회에는 영향 없음
      });

    return () => {
      ignore = true;
    };
  }, []);

  const filtered = (items ?? []).filter(
    (member) =>
      query.trim().length === 0 ||
      member.name.toLowerCase().includes(query.toLowerCase()) ||
      member.email.toLowerCase().includes(query.toLowerCase()),
  );

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div>
        <p className="text-xs font-bold tracking-wide text-wb-green">MEMBERS</p>
        <h1 className="mt-1 text-3xl font-bold">회원 현황</h1>
        <p className="mt-1 text-sm text-wb-secondary">회원 가입 현황과 상태를 확인합니다.</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Card>
          <p className="text-xs font-semibold text-wb-secondary">전체 회원</p>
          <p className="mt-1.5 text-xl font-bold">{totalElements.toLocaleString("ko-KR")}명</p>
        </Card>
        <Card>
          <p className="text-xs font-semibold text-wb-secondary">활성 회원</p>
          <p className="mt-1.5 text-xl font-bold">{activeCount.toLocaleString("ko-KR")}명</p>
        </Card>
        <Card>
          <p className="text-xs font-semibold text-wb-secondary">판매자 회원</p>
          <p className="mt-1.5 text-xl font-bold">{sellerCount.toLocaleString("ko-KR")}명</p>
        </Card>
      </div>

      <div className="flex flex-wrap gap-2">
        {TABS.map((tab) => (
          <button
            key={tab.label}
            onClick={() => {
              setStatus(tab.status);
              setPage(0);
            }}
            className={`rounded-full px-4 py-2 text-xs font-bold ${
              status === tab.status ? "bg-wb-green text-white" : "border border-wb-line bg-wb-surface text-wb-secondary"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="flex h-11 items-center gap-2.5 rounded-xl border border-wb-line bg-wb-surface px-3.5">
        <Search className="h-4 w-4 text-wb-secondary" />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="이름 또는 이메일 검색"
          className="w-full bg-transparent text-sm outline-none"
        />
      </div>

      {error && <Banner tone="error">{error}</Banner>}

      {items === null ? (
        <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : filtered.length === 0 ? (
        <EmptyState icon={Users} title="해당하는 회원이 없어요" message="다른 필터를 확인해보세요." />
      ) : (
        <>
          <div className="hidden overflow-hidden rounded-2xl border border-wb-line bg-wb-surface md:block">
            <div className="grid grid-cols-[1fr_120px_100px_100px] gap-3 border-b border-wb-line bg-wb-canvas/60 px-5 py-2.5 text-xs font-bold text-wb-secondary">
              <span>회원</span>
              <span>가입일</span>
              <span>역할</span>
              <span>상태</span>
            </div>
            {filtered.map((member) => (
              <div key={member.id} className="grid grid-cols-[1fr_120px_100px_100px] items-center gap-3 border-b border-wb-line px-5 py-3.5 last:border-0">
                <MemberIdentity name={member.name} email={member.email} />
                <span className="text-xs">{formatDateTime(member.createdAt)}</span>
                <span className="text-xs font-semibold">{member.role}</span>
                <StatusPill tone={STATUS_TONE[member.status]}>{STATUS_LABEL[member.status]}</StatusPill>
              </div>
            ))}
          </div>

          <div className="space-y-3 md:hidden">
            {filtered.map((member) => (
              <div key={member.id} className="flex items-center justify-between rounded-xl border border-wb-line bg-wb-surface p-4">
                <MemberIdentity name={member.name} email={member.email} />
                <div className="flex flex-col items-end gap-1.5">
                  <StatusPill tone={STATUS_TONE[member.status]}>{STATUS_LABEL[member.status]}</StatusPill>
                  <span className="text-xs text-wb-secondary">
                    {formatDateTime(member.createdAt)} · {member.role}
                  </span>
                </div>
              </div>
            ))}
          </div>

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
        </>
      )}
    </div>
  );
}

function MemberIdentity({ name, email }: { name: string; email: string }) {
  return (
    <div className="flex min-w-0 items-center gap-3">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-wb-light-green text-sm font-bold text-wb-green">
        {name.slice(0, 1)}
      </div>
      <div className="min-w-0">
        <p className="truncate text-sm font-bold">{name}</p>
        <p className="truncate text-xs text-wb-secondary">{email}</p>
      </div>
    </div>
  );
}
