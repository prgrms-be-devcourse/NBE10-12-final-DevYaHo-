"use client";

import { useEffect, useRef, useState } from "react";
import { Search, Store, Users } from "lucide-react";
import { ActionLogPanel } from "@/components/admin/ActionLogPanel";
import { ActionReasonModal } from "@/components/admin/ActionReasonModal";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Pagination } from "@/components/ui/Pagination";
import { StatusPill, Tag } from "@/components/ui/Tag";
import {
  approveSeller,
  listMembers,
  listSellerApplications,
  listSellerConversionActionLogs,
  listSellerSuspensionActionLogs,
  reactivateSeller,
  rejectSeller,
  suspendSeller,
} from "@/lib/api/admin";
import type { MemberStatus, MemberSummaryResponse, PageResponse, SellerInfoResponse, SellerStatus } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { showToast } from "@/lib/toast/toastStore";
import { invalidatePagedQuery, usePagedQuery } from "@/hooks/usePagedQuery";

type PendingAction = { id: number; kind: "approve" | "reject" | "suspend" | "reactivate" };

const ACTION_MODAL_CONFIG: Record<
  PendingAction["kind"],
  { title: string; actionLabel: string; confirmVariant: "primary" | "secondary" }
> = {
  approve: { title: "판매자 승인", actionLabel: "승인", confirmVariant: "primary" },
  reject: { title: "판매자 거절", actionLabel: "거절", confirmVariant: "secondary" },
  suspend: { title: "판매자 정지", actionLabel: "정지", confirmVariant: "secondary" },
  reactivate: { title: "판매자 정지 복귀", actionLabel: "정지 복귀", confirmVariant: "primary" },
};

const TABS: { status: SellerStatus; label: string }[] = [
  { status: "PENDING", label: "승인 대기" },
  { status: "APPROVED", label: "승인됨" },
  { status: "SUSPENDED", label: "정지됨" },
  { status: "REJECTED", label: "거절됨" },
];

const STATUS_TONE: Record<SellerStatus, "orange" | "green" | "red" | "neutral"> = {
  PENDING: "orange",
  APPROVED: "green",
  REJECTED: "red",
  SUSPENDED: "neutral",
};

const STATUS_LABEL: Record<SellerStatus, string> = {
  PENDING: "승인 대기",
  APPROVED: "승인됨",
  SUSPENDED: "정지됨",
  REJECTED: "거절됨",
};

function SellerApplicationsPanel({ status }: { status: SellerStatus }) {
  const [page, setPage] = useState(0);
  const [reloadToken, setReloadToken] = useState(0);
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);

  const { data, error, loading } = usePagedQuery<PageResponse<SellerInfoResponse>>(
    "admin-seller-applications",
    { status, page, reloadToken },
    () => listSellerApplications({ status, page }),
    "판매자 신청 목록을 불러오지 못했어요.",
  );
  const items = data?.content ?? null;
  const totalPages = data?.page.totalPages ?? 0;

  const ACTION_FN: Record<PendingAction["kind"], (id: number, reason: string) => Promise<void>> = {
    approve: approveSeller,
    reject: rejectSeller,
    suspend: suspendSeller,
    reactivate: reactivateSeller,
  };

  async function handleConfirmAction(reason: string) {
    if (!pendingAction) return;
    const { actionLabel } = ACTION_MODAL_CONFIG[pendingAction.kind];
    await ACTION_FN[pendingAction.kind](pendingAction.id, reason);
    invalidatePagedQuery("admin-seller-applications");
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
                      onClick={() => setPendingAction({ id: item.id, kind: "reject" })}
                    >
                      거절
                    </Button>
                    <Button
                      className="px-3 py-1.5 text-xs"
                      onClick={() => setPendingAction({ id: item.id, kind: "approve" })}
                    >
                      승인
                    </Button>
                  </div>
                )}
                {item.status === "SUSPENDED" && (
                  <Button className="px-3 py-1.5 text-xs" onClick={() => setPendingAction({ id: item.id, kind: "reactivate" })}>
                    정지 복귀
                  </Button>
                )}
              </div>
            </div>
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

const MEMBER_TABS: { status: MemberStatus | null; label: string }[] = [
  { status: null, label: "전체" },
  { status: "ACTIVE", label: "활성" },
  { status: "DORMANT", label: "휴면" },
  { status: "WITHDRAWN", label: "탈퇴" },
];

const MEMBER_STATUS_LABEL: Record<MemberStatus, string> = {
  ACTIVE: "정상",
  DORMANT: "휴면",
  WITHDRAWN: "탈퇴",
};

const MEMBER_STATUS_TONE: Record<MemberStatus, "green" | "orange" | "neutral"> = {
  ACTIVE: "green",
  DORMANT: "orange",
  WITHDRAWN: "neutral",
};

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

function MembersPanel() {
  const [status, setStatus] = useState<MemberStatus | null>(null);
  const [page, setPage] = useState(0);
  const [activeCount, setActiveCount] = useState(0);
  const [sellerCount, setSellerCount] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

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

  const { data, error, loading } = usePagedQuery<PageResponse<MemberSummaryResponse>>(
    "admin-members",
    { status, keyword: debouncedKeyword, page, reloadToken },
    () => listMembers({ status: status ?? undefined, keyword: debouncedKeyword || undefined, page, size: 10 }),
    "회원 목록을 불러오지 못했어요.",
  );
  const items = data?.content ?? null;
  const totalElements = data?.page.totalElements ?? 0;
  const totalPages = data?.page.totalPages ?? 0;

  useEffect(() => {
    let ignore = false;

    Promise.all([listMembers({ status: "ACTIVE", size: 1 }), listMembers({ role: "SELLER", size: 1 })])
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

  const MEMBER_ACTION_FN: Record<PendingAction["kind"], (id: number, reason: string) => Promise<void>> = {
    approve: approveSeller,
    reject: rejectSeller,
    suspend: suspendSeller,
    reactivate: reactivateSeller,
  };

  async function handleConfirmMemberAction(reason: string) {
    if (!pendingAction) return;
    const { actionLabel } = ACTION_MODAL_CONFIG[pendingAction.kind];
    await MEMBER_ACTION_FN[pendingAction.kind](pendingAction.id, reason);
    invalidatePagedQuery("admin-members");
    setReloadToken((t) => t + 1);
    showToast(`"${reason}" 사유로 ${actionLabel} 처리되었습니다.`);
    setPendingAction(null);
  }

  function SellerActionButton({ member }: { member: MemberSummaryResponse }) {
    if (member.sellerId === null || member.sellerStatus === null) return null;
    if (member.sellerStatus === "APPROVED") {
      return (
        <Button
          variant="secondary"
          className="px-3 py-1.5 text-xs"
          onClick={() => setPendingAction({ id: member.sellerId as number, kind: "suspend" })}
        >
          정지
        </Button>
      );
    }
    if (member.sellerStatus === "SUSPENDED") {
      return (
        <Button
          className="px-3 py-1.5 text-xs"
          onClick={() => setPendingAction({ id: member.sellerId as number, kind: "reactivate" })}
        >
          정지 복귀
        </Button>
      );
    }
    return null;
  }

  return (
    <div className="space-y-6">
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
        {MEMBER_TABS.map((tab) => (
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
          value={keyword}
          onChange={(e) => handleKeywordChange(e.target.value)}
          placeholder="이메일로 검색"
          className="w-full bg-transparent text-sm outline-none"
        />
      </div>

      {error && <Banner tone="error">{error}</Banner>}

      {loading && items === null ? (
        <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : items === null || items.length === 0 ? (
        <EmptyState icon={Users} title="해당하는 회원이 없어요" message="다른 필터를 확인해보세요." />
      ) : (
        <>
          <div className="hidden overflow-hidden rounded-2xl border border-wb-line bg-wb-surface md:block">
            <div className="grid grid-cols-[1fr_150px_100px_100px_100px] gap-3 border-b border-wb-line bg-wb-canvas/60 px-5 py-2.5 text-xs font-bold text-wb-secondary">
              <span className="pl-2">회원</span>
              <span className="text-center">가입일</span>
              <span className="text-center">역할</span>
              <span className="text-center">상태</span>
              <span className="text-center">관리</span>
            </div>
            {items.map((member) => (
              <div key={member.id} className="grid grid-cols-[1fr_150px_100px_100px_100px] items-center gap-3 border-b border-wb-line px-5 py-3.5 last:border-0">
                <MemberIdentity name={member.name} email={member.email} />
                <span className="text-center text-xs">{formatDateTime(member.createdAt)}</span>
                <span className="text-center text-xs font-semibold">{member.role}</span>
                <div className="flex justify-center">
                  <StatusPill tone={MEMBER_STATUS_TONE[member.status]}>{MEMBER_STATUS_LABEL[member.status]}</StatusPill>
                </div>
                <div className="flex justify-center">
                  <SellerActionButton member={member} />
                </div>
              </div>
            ))}
          </div>

          <div className="space-y-3 md:hidden">
            {items.map((member) => (
              <div key={member.id} className="flex items-center justify-between rounded-xl border border-wb-line bg-wb-surface p-4">
                <MemberIdentity name={member.name} email={member.email} />
                <div className="flex flex-col items-end gap-1.5">
                  <StatusPill tone={MEMBER_STATUS_TONE[member.status]}>{MEMBER_STATUS_LABEL[member.status]}</StatusPill>
                  <span className="text-xs text-wb-secondary">
                    {formatDateTime(member.createdAt)} · {member.role}
                  </span>
                  <SellerActionButton member={member} />
                </div>
              </div>
            ))}
          </div>

          <Pagination page={page} totalPages={totalPages} onChange={setPage} />
        </>
      )}

      <ActionReasonModal
        open={pendingAction !== null}
        title={pendingAction ? ACTION_MODAL_CONFIG[pendingAction.kind].title : ""}
        actionLabel={pendingAction ? ACTION_MODAL_CONFIG[pendingAction.kind].actionLabel : ""}
        confirmVariant={pendingAction ? ACTION_MODAL_CONFIG[pendingAction.kind].confirmVariant : "primary"}
        onClose={() => setPendingAction(null)}
        onConfirm={handleConfirmMemberAction}
      />
    </div>
  );
}

const SELLER_HISTORY_TABS: { key: "conversion" | "suspension"; label: string }[] = [
  { key: "conversion", label: "승인/거절 이력" },
  { key: "suspension", label: "정지/정지복귀 이력" },
];

function SellerActionLogSection() {
  const [tab, setTab] = useState<"conversion" | "suspension">("conversion");

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        {SELLER_HISTORY_TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`rounded-full px-4 py-2 text-xs font-bold ${
              tab === t.key ? "bg-wb-green text-white" : "border border-wb-line bg-wb-surface text-wb-secondary"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>
      {tab === "conversion" ? (
        <ActionLogPanel
          cacheNamespace="admin-seller-conversion-action-logs"
          fetcher={listSellerConversionActionLogs}
          targetLabelHeader="판매자"
          emptyMessage="아직 승인/거절 처리된 판매자 신청이 없어요."
        />
      ) : (
        <ActionLogPanel
          cacheNamespace="admin-seller-suspension-action-logs"
          fetcher={listSellerSuspensionActionLogs}
          targetLabelHeader="판매자"
          emptyMessage="아직 정지/정지복귀 처리된 판매자가 없어요."
        />
      )}
    </div>
  );
}

const VIEW_TABS: { key: "sellers" | "members" | "history"; label: string }[] = [
  { key: "sellers", label: "판매자 심사" },
  { key: "members", label: "회원 현황" },
  { key: "history", label: "처리 이력" },
];

export default function AdminSellersPage() {
  const [view, setView] = useState<"sellers" | "members" | "history">("sellers");
  const [status, setStatus] = useState<SellerStatus>("PENDING");

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div>
        <p className="text-xs font-bold tracking-wide text-wb-green">MEMBERS</p>
        <h1 className="mt-1 text-3xl font-bold">회원 관리</h1>
        <p className="mt-1 text-sm text-wb-secondary">판매자 전환 신청을 검토하고 회원 현황을 확인합니다.</p>
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

      {view === "sellers" ? (
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

          <SellerApplicationsPanel key={status} status={status} />
        </>
      ) : view === "members" ? (
        <MembersPanel />
      ) : (
        <SellerActionLogSection />
      )}
    </div>
  );
}
