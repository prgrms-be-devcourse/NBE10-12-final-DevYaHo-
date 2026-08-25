"use client";

import { useState } from "react";
import { Search } from "lucide-react";
import { Card } from "@/components/ui/Card";
import { StatusPill } from "@/components/ui/Tag";
import { compactCount } from "@/lib/format";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";

export default function AdminMembersPage() {
  const { adminMembers } = useDemoStore();
  const [query, setQuery] = useState("");

  const filtered = adminMembers.filter(
    (member) =>
      query.trim().length === 0 ||
      member.nickname.toLowerCase().includes(query.toLowerCase()) ||
      member.email.toLowerCase().includes(query.toLowerCase()),
  );

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div>
        <p className="text-xs font-bold tracking-wide text-wb-green">MEMBERS</p>
        <h1 className="mt-1 text-3xl font-bold">회원 현황</h1>
        <p className="mt-1 text-sm text-wb-secondary">회원 가입과 공동구매 참여 현황을 확인합니다.</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Card>
          <p className="text-xs font-semibold text-wb-secondary">전체 회원</p>
          <p className="mt-1.5 text-xl font-bold">{compactCount(2481, "명")}</p>
          <p className="text-xs font-semibold text-wb-green">이번 달 +184</p>
        </Card>
        <Card>
          <p className="text-xs font-semibold text-wb-secondary">활성 회원</p>
          <p className="mt-1.5 text-xl font-bold">{compactCount(1926, "명")}</p>
          <p className="text-xs font-semibold text-wb-green">최근 30일</p>
        </Card>
        <Card>
          <p className="text-xs font-semibold text-wb-secondary">재구매 회원</p>
          <p className="mt-1.5 text-xl font-bold">38%</p>
          <p className="text-xs font-semibold text-wb-green">2회 이상 참여</p>
        </Card>
      </div>

      <div className="flex h-11 items-center gap-2.5 rounded-xl border border-wb-line bg-wb-surface px-3.5">
        <Search className="h-4 w-4 text-wb-secondary" />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="닉네임 또는 이메일 검색"
          className="w-full bg-transparent text-sm outline-none"
        />
      </div>

      <div className="hidden overflow-hidden rounded-2xl border border-wb-line bg-wb-surface md:block">
        <div className="grid grid-cols-[1fr_120px_100px_100px] gap-3 border-b border-wb-line bg-wb-canvas/60 px-5 py-2.5 text-xs font-bold text-wb-secondary">
          <span>회원</span>
          <span>가입일</span>
          <span>참여 횟수</span>
          <span>상태</span>
        </div>
        {filtered.map((member) => (
          <div key={member.id} className="grid grid-cols-[1fr_120px_100px_100px] items-center gap-3 border-b border-wb-line px-5 py-3.5 last:border-0">
            <MemberIdentity nickname={member.nickname} email={member.email} />
            <span className="text-xs">{member.joinedAt}</span>
            <span className="text-xs font-semibold">{member.participationCount}회</span>
            <StatusPill tone={member.status === "정상" ? "green" : "orange"}>{member.status}</StatusPill>
          </div>
        ))}
      </div>

      <div className="space-y-3 md:hidden">
        {filtered.map((member) => (
          <div key={member.id} className="flex items-center justify-between rounded-xl border border-wb-line bg-wb-surface p-4">
            <MemberIdentity nickname={member.nickname} email={member.email} />
            <div className="flex flex-col items-end gap-1.5">
              <StatusPill tone={member.status === "정상" ? "green" : "orange"}>{member.status}</StatusPill>
              <span className="text-xs text-wb-secondary">
                {member.joinedAt} · 참여 {member.participationCount}회
              </span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function MemberIdentity({ nickname, email }: { nickname: string; email: string }) {
  return (
    <div className="flex min-w-0 items-center gap-3">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-wb-light-green text-sm font-bold text-wb-green">
        {nickname.slice(0, 1)}
      </div>
      <div className="min-w-0">
        <p className="truncate text-sm font-bold">{nickname}</p>
        <p className="truncate text-xs text-wb-secondary">{email}</p>
      </div>
    </div>
  );
}
