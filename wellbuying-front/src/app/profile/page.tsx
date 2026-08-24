"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { RequireAuth } from "@/components/auth/RequireAuth";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Banner } from "@/components/ui/Banner";
import { useAuth } from "@/lib/auth/AuthProvider";
import { logout, logoutAll } from "@/lib/api/auth";
import { ApiError } from "@/lib/api/http";

const ROLE_LABEL: Record<string, string> = {
  BUYER: "구매자",
  SELLER: "생산자",
  ADMIN: "관리자",
};

function ProfileContent() {
  const { member, setMember } = useAuth();
  const router = useRouter();
  const [loadingAction, setLoadingAction] = useState<"logout" | "logout-all" | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleLogout(all: boolean) {
    setError(null);
    setLoadingAction(all ? "logout-all" : "logout");
    try {
      await (all ? logoutAll() : logout());
      setMember(null);
      router.push("/login");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "로그아웃에 실패했어요.");
    } finally {
      setLoadingAction(null);
    }
  }

  if (!member) return null;

  return (
    <div className="mx-auto w-full max-w-md px-4 py-16">
      <Link href="/home" className="mb-4 inline-block text-xs font-semibold text-wb-secondary hover:text-wb-ink">
        ← 홈으로
      </Link>
      <Card className="space-y-5">
        <div>
          <h1 className="text-xl font-semibold">내 정보</h1>
          <p className="text-xs text-wb-secondary">계정 정보와 세션을 관리해요.</p>
        </div>

        <dl className="space-y-3 text-sm">
          <div className="flex justify-between">
            <dt className="text-wb-secondary">이름</dt>
            <dd className="font-medium">{member.name}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-wb-secondary">이메일</dt>
            <dd className="font-medium">{member.email}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-wb-secondary">역할</dt>
            <dd className="font-medium">{ROLE_LABEL[member.role] ?? member.role}</dd>
          </div>
        </dl>

        {member.role === "BUYER" && (
          <Link
            href="/seller/apply"
            className="block rounded-lg border border-wb-line bg-wb-canvas px-3 py-2.5 text-center text-sm font-semibold text-wb-green hover:bg-wb-light-green/40"
          >
            생산자로 신청하기
          </Link>
        )}

        {error && <Banner tone="error">{error}</Banner>}

        <div className="flex gap-2">
          <Button
            variant="secondary"
            className="flex-1"
            onClick={() => handleLogout(false)}
            loading={loadingAction === "logout"}
          >
            로그아웃
          </Button>
          <Button
            variant="secondary"
            className="flex-1"
            onClick={() => handleLogout(true)}
            loading={loadingAction === "logout-all"}
          >
            전체 로그아웃
          </Button>
        </div>
      </Card>
    </div>
  );
}

export default function ProfilePage() {
  return (
    <RequireAuth>
      <ProfileContent />
    </RequireAuth>
  );
}
