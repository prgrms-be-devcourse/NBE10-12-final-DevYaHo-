"use client";

import { FormEvent, Suspense, useEffect, useState, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { User } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Banner } from "@/components/ui/Banner";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { useAuth } from "@/lib/auth/AuthProvider";
import DaumPostcode from "react-daum-postcode";
import {
  getDevices,
  getSocialAccounts,
  issueLinkUrl,
  logout,
  logoutAll,
  unlinkProvider,
  updateProfile,
  withdraw,
  getSellerInfo,
  requestProfileImageUploadUrl,
  sendPasswordReissueCode,
  verifyPasswordReissueCode,
  resetPassword,
} from "@/lib/api/auth";
import { listMyAddresses, createMyAddress, deleteMyAddress } from "@/lib/api/address";
import { ApiError } from "@/lib/api/http";
import { clearTokens, getDeviceId, getCachedDevices, saveCachedDevices } from "@/lib/auth/token-storage";
import type { DeviceSessionResponse, MemberResponse, OAuthProvider, BuyerAddressResponse } from "@/lib/api/types";

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
      .then((data) => {
        if (data && data.length > 0) {
          saveCachedDevices(data);
          setDevices(data);
        } else {
          // 백엔드 세션이 날아갔지만 JWT는 살아있어 빈 배열이 올 경우 캐시 사용
          const cached = getCachedDevices();
          setDevices(cached || []);
        }
      })
      .catch(() => {
        const cached = getCachedDevices();
        setDevices(cached || []);
      });
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
  onRequestPasswordReset,
}: {
  onClose: () => void;
  initialName: string;
  initialProfileImageUrl: string;
  onSaved: (member: MemberResponse) => void;
  onRequestPasswordReset: () => void;
}) {
  const [name, setName] = useState(initialName);
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string>(initialProfileImageUrl);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (file) {
      const url = URL.createObjectURL(file);
      setPreviewUrl(url);
      return () => URL.revokeObjectURL(url);
    }
  }, [file]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      let finalImageUrl = initialProfileImageUrl;
      if (file) {
        const { uploadUrl, profileImageUrl } = await requestProfileImageUploadUrl(file.type);
        const uploadRes = await fetch(uploadUrl, {
          method: "PUT",
          body: file,
          headers: {
            "Content-Type": file.type,
            "x-amz-tagging": "pending=true",
          },
        });
        if (!uploadRes.ok) throw new Error("이미지 업로드에 실패했어요.");
        finalImageUrl = profileImageUrl;
      }

      const updated = await updateProfile({ name, profileImageUrl: finalImageUrl || undefined });
      onSaved(updated);
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : "정보 수정에 실패했어요.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open onClose={onClose} title="정보 수정" width="380px">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="flex flex-col items-center gap-2">
          {previewUrl ? (
            /* eslint-disable-next-line @next/next/no-img-element */
            <img
              src={previewUrl}
              alt="프로필 미리보기"
              className="h-24 w-24 rounded-full border border-wb-line object-cover"
            />
          ) : (
            <div className="flex h-24 w-24 items-center justify-center rounded-full border border-wb-line bg-wb-canvas text-wb-secondary">
              <User className="h-10 w-10" />
            </div>
          )}
          <input
            type="file"
            accept="image/jpeg, image/png, image/webp"
            className="hidden"
            ref={fileInputRef}
            onChange={(e) => setFile(e.target.files?.[0] || null)}
          />
          <Button type="button" variant="secondary" className="px-3 py-1 text-xs" onClick={() => fileInputRef.current?.click()}>
            이미지 변경
          </Button>
        </div>
        
        <TextField label="이름" value={name} onChange={(e) => setName(e.target.value)} required />
        {error && <Banner tone="error">{error}</Banner>}
        <div className="flex items-center justify-between border-t border-wb-line pt-4 mt-2">
          <Button type="button" variant="secondary" className="px-3 py-1.5 text-xs text-wb-secondary" onClick={onRequestPasswordReset}>
            비밀번호 재설정
          </Button>
          <div className="flex gap-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              취소
            </Button>
            <Button type="submit" loading={submitting} disabled={name.length === 0}>
              저장
            </Button>
          </div>
        </div>
      </form>
    </Modal>
  );
}

function AddressSection() {
  const [addresses, setAddresses] = useState<BuyerAddressResponse[] | null>(null);
  const [showAdd, setShowAdd] = useState(false);
  const [isSearching, setIsSearching] = useState(false);
  
  const [zipcode, setZipcode] = useState("");
  const [address, setAddress] = useState("");
  const [addressDetail, setAddressDetail] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [deleteTarget, setDeleteTarget] = useState<number | null>(null);

  const load = () => {
    listMyAddresses().then(setAddresses).catch(() => setAddresses([]));
  };

  useEffect(() => { load(); }, []);

  async function handleAdd(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await createMyAddress({ address, addressDetail, zipcode });
      setZipcode("");
      setAddress("");
      setAddressDetail("");
      setShowAdd(false);
      load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "배송지 추가에 실패했어요.");
    } finally {
      setLoading(false);
    }
  }

  async function performDelete() {
    if (deleteTarget === null) return;
    try {
      await deleteMyAddress(deleteTarget);
      load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "배송지 삭제에 실패했어요.");
    } finally {
      setDeleteTarget(null);
    }
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const handleCompletePostcode = (data: any) => {
    setZipcode(data.zonecode);
    setAddress(data.address);
    setIsSearching(false);
  };

  if (!addresses) return null;

  return (
    <div className="space-y-3 border-t border-wb-line pt-5">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold">나의 배송지</h2>
        <Button variant="secondary" className="px-2 py-1 text-xs" onClick={() => setShowAdd(true)}>
          + 추가
        </Button>
      </div>

      {addresses.length === 0 ? (
        <p className="text-xs text-wb-secondary">등록된 배송지가 없어요.</p>
      ) : (
        <ul className="space-y-2">
          {addresses.map((addr) => (
            <li key={addr.id} className="flex flex-col gap-1 rounded-lg border border-wb-line bg-wb-canvas px-3 py-2 text-sm">
              <div className="flex justify-between">
                <span className="font-semibold">[{addr.zipcode}]</span>
                <button onClick={() => setDeleteTarget(addr.id)} className="text-xs text-red-500 hover:underline">삭제</button>
              </div>
              <span className="text-wb-ink">{addr.address} {addr.addressDetail}</span>
            </li>
          ))}
        </ul>
      )}

      {showAdd && (
        <Modal open onClose={() => setShowAdd(false)} title="배송지 추가" width="400px">
          {isSearching ? (
            <div className="space-y-2">
               <DaumPostcode 
                 onComplete={handleCompletePostcode} 
                 autoClose={false} 
                 style={{ height: "400px", width: "100%" }} 
               />
               <Button variant="secondary" className="w-full" onClick={() => setIsSearching(false)}>닫기</Button>
            </div>
          ) : (
            <form onSubmit={handleAdd} className="space-y-4">
              <div className="flex gap-2 items-end">
                <div className="flex-1">
                  <TextField label="우편번호" value={zipcode} readOnly required />
                </div>
                <Button type="button" variant="secondary" onClick={() => setIsSearching(true)} className="h-11">
                  우편번호 검색
                </Button>
              </div>
              <TextField label="주소" value={address} readOnly required />
              <TextField label="상세주소" value={addressDetail} onChange={(e) => setAddressDetail(e.target.value)} />
              
              {error && <Banner tone="error">{error}</Banner>}
              
              <div className="flex justify-end gap-2">
                <Button type="button" variant="secondary" onClick={() => setShowAdd(false)}>취소</Button>
                <Button type="submit" loading={loading} disabled={zipcode.length === 0 || address.length === 0}>저장</Button>
              </div>
            </form>
          )}
        </Modal>
      )}

      <ConfirmDialog
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        onConfirm={performDelete}
        title="배송지 삭제"
        message="이 배송지를 정말 삭제하시겠습니까?"
        confirmLabel="삭제"
        destructive
      />
    </div>
  );
}

function SellerApplicationSection() {
  const [status, setStatus] = useState<string | null>(null);

  useEffect(() => {
    getSellerInfo()
      .then((info) => setStatus(info.status))
      .catch(() => {
        // 404 or other error means not applied
        setStatus("NONE");
      });
  }, []);

  if (status === null) return null; // loading

  if (status === "PENDING") {
    return (
      <div className="rounded-lg border border-wb-line bg-wb-canvas px-3 py-2.5 text-center text-sm font-semibold text-wb-orange">
        생산자 심사 진행 중
      </div>
    );
  }

  if (status === "TERMINATED") {
    return (
      <div className="flex flex-col gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2.5 text-center text-sm">
        <span className="font-semibold text-red-600">생산자 신청이 반려되었어요.</span>
        <Link href="/seller/apply" className="text-xs font-semibold text-wb-green hover:underline">
          다시 신청하기
        </Link>
      </div>
    );
  }

  return (
    <Link
      href="/seller/apply"
      className="block rounded-lg border border-wb-line bg-wb-canvas px-3 py-2.5 text-center text-sm font-semibold text-wb-green hover:bg-wb-light-green/40"
    >
      생산자로 신청하기
    </Link>
  );
}


function PasswordResetModal({ email, onClose }: { email: string; onClose: () => void }) {
  const router = useRouter();
  const { setMember } = useAuth();
  
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [code, setCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  async function handleSend() {
    setLoading(true); setError(null); setSuccessMsg(null);
    try {
      await sendPasswordReissueCode(email);
      setSuccessMsg("이메일로 인증 코드가 발송되었습니다.");
      setStep(2);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "이메일 발송에 실패했어요.");
    } finally {
      setLoading(false);
    }
  }

  async function handleVerify(e: FormEvent) {
    e.preventDefault();
    setLoading(true); setError(null); setSuccessMsg(null);
    try {
      await verifyPasswordReissueCode(email, code);
      setSuccessMsg("인증이 완료되었습니다. 새 비밀번호를 입력해주세요.");
      setStep(3);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "인증에 실패했어요.");
    } finally {
      setLoading(false);
    }
  }

  async function handleReset(e: FormEvent) {
    e.preventDefault();
    setLoading(true); setError(null); setSuccessMsg(null);
    try {
      await resetPassword(email, newPassword);
      alert("비밀번호가 성공적으로 재설정되었습니다. 다시 로그인해주세요.");
      clearTokens();
      setMember(null);
      router.push("/login");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "비밀번호 재설정에 실패했어요.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <Modal open onClose={onClose} title="비밀번호 재설정" width="380px">
      <div className="space-y-4">
        {step === 1 && (
          <div className="space-y-4">
            <p className="text-sm text-wb-secondary">
              현재 로그인된 계정({email})으로 비밀번호 재설정 코드를 발송합니다.
            </p>
            {successMsg && <Banner tone="success">{successMsg}</Banner>}
            {error && <Banner tone="error">{error}</Banner>}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={onClose}>취소</Button>
              <Button type="button" onClick={handleSend} loading={loading}>인증 코드 발송</Button>
            </div>
          </div>
        )}
        
        {step === 2 && (
          <form onSubmit={handleVerify} className="space-y-4">
            <p className="text-sm text-wb-secondary">
              이메일로 발송된 6자리 인증 코드를 입력해주세요.
            </p>
            <TextField label="인증 코드" value={code} onChange={(e) => setCode(e.target.value)} required />
            {successMsg && <Banner tone="success">{successMsg}</Banner>}
            {error && <Banner tone="error">{error}</Banner>}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={onClose}>취소</Button>
              <Button type="submit" loading={loading} disabled={code.length === 0}>코드 인증</Button>
            </div>
          </form>
        )}
        
        {step === 3 && (
          <form onSubmit={handleReset} className="space-y-4">
            <p className="text-sm text-wb-secondary">새롭게 사용할 비밀번호를 입력해주세요.</p>
            <TextField label="새 비밀번호" type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required />
            {error && <Banner tone="error">{error}</Banner>}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={onClose}>취소</Button>
              <Button type="submit" loading={loading} disabled={newPassword.length < 8}>재설정 완료</Button>
            </div>
          </form>
        )}
      </div>
    </Modal>
  );
}

// showHeader=false: 바깥에서 이미 "내 정보" 타이틀을 보여주는 컨테이너에 끼워 넣을 때 중복 타이틀을 없앤다.
export function ProfileContent({ showHeader = true }: { showHeader?: boolean } = {}) {
  const { member, setMember } = useAuth();
  const router = useRouter();
  const [loadingAction, setLoadingAction] = useState<"logout" | "logout-all" | "withdraw" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const [passwordResetOpen, setPasswordResetOpen] = useState(false);

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
    <>
      <div className="space-y-5">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-4">
            {member.profileImageUrl ? (
              /* eslint-disable-next-line @next/next/no-img-element */
              <img
                src={member.profileImageUrl}
                alt="프로필 이미지"
                className="h-12 w-12 rounded-full border border-wb-line object-cover"
              />
            ) : (
              <div className="flex h-12 w-12 items-center justify-center rounded-full border border-wb-line bg-wb-canvas text-wb-secondary">
                <User className="h-6 w-6" />
              </div>
            )}
            {showHeader ? (
              <div>
                <h1 className="text-xl font-semibold">내 정보</h1>
                <p className="text-xs text-wb-secondary">계정 정보와 세션을 관리해요.</p>
              </div>
            ) : (
              <div />
            )}
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

        {member.role === "BUYER" && <SellerApplicationSection />}

        {error && <Banner tone="error">{error}</Banner>}

        <AddressSection />

        <div className="flex gap-2 border-t border-wb-line pt-5">
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
      </div>


      {passwordResetOpen && (
        <PasswordResetModal email={member.email} onClose={() => setPasswordResetOpen(false)} />
      )}
      {editOpen && (
        <EditProfileModal
          onClose={() => setEditOpen(false)}
          initialName={member.name}
          initialProfileImageUrl={member.profileImageUrl ?? ""}
          onSaved={setMember}
          onRequestPasswordReset={() => {
            setEditOpen(false);
            setPasswordResetOpen(true);
          }}
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
    </>
  );
}
