"use client";

import { FormEvent, Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { RequireAuth } from "@/components/auth/RequireAuth";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Banner } from "@/components/ui/Banner";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { useAuth } from "@/lib/auth/AuthProvider";
import {
  getDevices,
  getSocialAccounts,
  issueLinkUrl,
  logout,
  logoutAll,
  unlinkProvider,
  updateProfile,
  withdraw,
} from "@/lib/api/auth";
import { ApiError } from "@/lib/api/http";
import { clearTokens, getDeviceId } from "@/lib/auth/token-storage";
import type { DeviceSessionResponse, MemberResponse, OAuthProvider } from "@/lib/api/types";

const ROLE_LABEL: Record<string, string> = {
  BUYER: "구매자",
  SELLER: "생산자",
  ADMIN: "관리자",
};

const SOCIAL_PROVIDERS: { provider: OAuthProvider; label: string }[] = [
  { provider: "GOOGLE", label: "구글" },
  { provider: "KAKAO", label: "카카오" },
];

function SocialAccountsSection() {
  const searchParams = useSearchParams();
  const [providers, setProviders] = useState<OAuthProvider[] | null>(null);
  const [actionProvider, setActionProvider] = useState<OAuthProvider | null>(null);
  const [unlinkTarget, setUnlinkTarget] = useState<OAuthProvider | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    getSocialAccounts()
      .then((response) => setProviders(response.providers))
      .catch(() => setProviders([]));
  }, []);

  const linked = searchParams.get("linked") === "true" ? searchParams.get("provider") : null;

  async function handleLink(provider: OAuthProvider) {
    setActionError(null);
    setActionProvider(provider);
    try {
      const response = await issueLinkUrl(provider);
      window.location.assign(response.redirectUrl);
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : "연동 요청에 실패했어요.");
      setActionProvider(null);
    }
  }

  async function handleUnlink(provider: OAuthProvider) {
    setActionError(null);
    setActionProvider(provider);
    try {
      await unlinkProvider(provider);
      setProviders((prev) => (prev ?? []).filter((p) => p !== provider));
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : "연동 해제에 실패했어요.");
    } finally {
      setActionProvider(null);
    }
  }

  return (
    <div className="space-y-3 border-t border-wb-line pt-5">
      <h2 className="text-sm font-semibold">연동된 소셜 계정</h2>

      {linked && (
        <Banner tone="success">
          {SOCIAL_PROVIDERS.find((p) => p.provider === linked)?.label ?? linked} 계정이 연동됐어요.
        </Banner>
      )}
      {actionError && <Banner tone="error">{actionError}</Banner>}

      <div className="space-y-2">
        {SOCIAL_PROVIDERS.map(({ provider, label }) => {
          const isLinked = providers?.includes(provider) ?? false;
          return (
            <div
              key={provider}
              className="flex items-center justify-between rounded-lg border border-wb-line bg-wb-canvas px-3 py-2.5"
            >
              <div className="flex items-center gap-2 text-sm">
                <span className="font-medium">{label}</span>
                {isLinked && (
                  <span className="rounded-full bg-wb-light-green/60 px-2 py-0.5 text-xs font-semibold text-wb-green">
                    연동됨
                  </span>
                )}
              </div>
              <Button
                variant="secondary"
                className="px-3 py-1.5 text-xs"
                loading={actionProvider === provider}
                disabled={providers === null}
                onClick={() => (isLinked ? setUnlinkTarget(provider) : handleLink(provider))}
              >
                {isLinked ? "해제" : "연동하기"}
              </Button>
            </div>
          );
        })}
      </div>

      <ConfirmDialog
        open={unlinkTarget !== null}
        onClose={() => setUnlinkTarget(null)}
        onConfirm={() => unlinkTarget && handleUnlink(unlinkTarget)}
        title="소셜 계정 연동 해제"
        message="연동을 해제하면 이 계정으로 더 이상 소셜 로그인을 할 수 없어요."
        confirmLabel="해제"
        destructive
      />
    </div>
  );
}

function DeviceListSection() {
  const [devices, setDevices] = useState<DeviceSessionResponse[] | null>(null);
  const currentDeviceId = getDeviceId();

  useEffect(() => {
    getDevices()
      .then(setDevices)
      .catch(() => setDevices([]));
  }, []);

  if (!devices || devices.length === 0) return null;

  return (
    <div className="space-y-3 border-t border-wb-line pt-5">
      <h2 className="text-sm font-semibold">로그인된 기기</h2>
      <ul className="space-y-2 text-xs">
        {devices.map((device) => (
          <li
            key={device.deviceId}
            className="flex items-center justify-between rounded-lg border border-wb-line bg-wb-canvas px-3 py-2"
          >
            <span className="text-wb-secondary">
              마지막 사용: {new Date(device.lastUsedAt * 1000).toLocaleString("ko-KR")}
            </span>
            {device.deviceId === currentDeviceId && (
              <span className="rounded-full bg-wb-light-green/60 px-2 py-0.5 font-semibold text-wb-green">
                현재 기기
              </span>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}

function EditProfileModal({
  onClose,
  initialName,
  initialProfileImageUrl,
  onSaved,
}: {
  onClose: () => void;
  initialName: string;
  initialProfileImageUrl: string;
  onSaved: (member: MemberResponse) => void;
}) {
  const [name, setName] = useState(initialName);
  const [profileImageUrl, setProfileImageUrl] = useState(initialProfileImageUrl);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const updated = await updateProfile({ name, profileImageUrl: profileImageUrl || undefined });
      onSaved(updated);
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "정보 수정에 실패했어요.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open onClose={onClose} title="정보 수정" width="380px">
      <form onSubmit={handleSubmit} className="space-y-4">
        <TextField label="이름" value={name} onChange={(e) => setName(e.target.value)} required />
        <TextField
          label="프로필 이미지 URL (선택)"
          value={profileImageUrl}
          onChange={(e) => setProfileImageUrl(e.target.value)}
        />
        {error && <Banner tone="error">{error}</Banner>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" loading={submitting} disabled={name.length === 0}>
            저장
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function ProfileContent() {
  const { member, setMember } = useAuth();
  const router = useRouter();
  const [loadingAction, setLoadingAction] = useState<"logout" | "logout-all" | "withdraw" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [withdrawOpen, setWithdrawOpen] = useState(false);

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

  async function handleWithdraw() {
    setError(null);
    setLoadingAction("withdraw");
    try {
      await withdraw();
      clearTokens();
      setMember(null);
      router.push("/login");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "회원 탈퇴에 실패했어요.");
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
        <div className="flex items-start justify-between gap-3">
          <div>
            <h1 className="text-xl font-semibold">내 정보</h1>
            <p className="text-xs text-wb-secondary">계정 정보와 세션을 관리해요.</p>
          </div>
          <Button variant="secondary" className="shrink-0 px-3 py-1.5 text-xs" onClick={() => setEditOpen(true)}>
            정보 수정
          </Button>
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

        <Suspense fallback={null}>
          <SocialAccountsSection />
        </Suspense>

        <DeviceListSection />

        <button
          type="button"
          onClick={() => setWithdrawOpen(true)}
          className="w-full border-t border-wb-line pt-4 text-center text-xs font-semibold text-wb-secondary hover:text-red-600"
        >
          회원 탈퇴
        </button>
      </Card>

      {editOpen && (
        <EditProfileModal
          onClose={() => setEditOpen(false)}
          initialName={member.name}
          initialProfileImageUrl={member.profileImageUrl ?? ""}
          onSaved={setMember}
        />
      )}

      <ConfirmDialog
        open={withdrawOpen}
        onClose={() => setWithdrawOpen(false)}
        onConfirm={handleWithdraw}
        title="회원 탈퇴"
        message="탈퇴하면 계정과 모든 세션이 즉시 종료돼요. 이 작업은 되돌릴 수 없어요."
        confirmLabel="탈퇴하기"
        destructive
      />
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
