"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { Banner } from "@/components/ui/Banner";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ApiError } from "@/lib/api/http";
import {
  getOAuthAuthorizationUrl,
  login,
  sendVerificationCode,
  sellerSignup,
  signup,
  verifyEmail,
} from "@/lib/api/auth";
import type { OAuthProvider } from "@/lib/api/types";

const SOCIAL_PROVIDERS: { provider: OAuthProvider; label: string }[] = [
  { provider: "GOOGLE", label: "구글로 계속하기" },
  { provider: "KAKAO", label: "카카오로 계속하기" },
];

type Mode = "login" | "signup";

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function LoginPage() {
  const [mode, setMode] = useState<Mode>("login");
  const router = useRouter();
  const { refreshMember } = useAuth();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [asProducer, setAsProducer] = useState(false);
  const [bankCode, setBankCode] = useState("");
  const [bankName, setBankName] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [accountHolder, setAccountHolder] = useState("");
  const [companyName, setCompanyName] = useState("");

  const [emailVerified, setEmailVerified] = useState(false);
  const [codeSent, setCodeSent] = useState(false);
  const [code, setCode] = useState("");
  const [sendingCode, setSendingCode] = useState(false);
  const [verifyingCode, setVerifyingCode] = useState(false);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  function resetEmailVerification() {
    setEmailVerified(false);
    setCodeSent(false);
    setCode("");
  }

  async function handleSendCode() {
    setError(null);
    setNotice(null);
    if (!EMAIL_PATTERN.test(email)) {
      setError("올바른 이메일 형식을 입력해주세요.");
      return;
    }
    setSendingCode(true);
    try {
      await sendVerificationCode(email);
      setCodeSent(true);
      setNotice("인증 코드를 이메일로 보냈어요.");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "인증 코드 발송에 실패했어요.");
    } finally {
      setSendingCode(false);
    }
  }

  async function handleVerifyCode() {
    setError(null);
    setNotice(null);
    setVerifyingCode(true);
    try {
      await verifyEmail(email, code);
      setEmailVerified(true);
      setNotice("이메일 인증이 완료됐어요.");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "인증 코드가 올바르지 않아요.");
    } finally {
      setVerifyingCode(false);
    }
  }

  async function handleLogin() {
    await login(email, password);
    await refreshMember();
    router.push("/home");
  }

  async function handleSignup() {
    if (asProducer) {
      await sellerSignup({
        email,
        password,
        name,
        bankCode,
        bankName,
        accountNumber,
        accountHolder,
        companyName: companyName || undefined,
      });
    } else {
      await signup({ email, password, name });
    }
    await login(email, password);
    await refreshMember();
    router.push("/home");
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      if (mode === "login") {
        await handleLogin();
      } else {
        await handleSignup();
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "요청 처리 중 오류가 발생했어요.");
    } finally {
      setSubmitting(false);
    }
  }

  const canSubmitLogin = email.length > 0 && password.length > 0;
  const canSubmitSignup =
    emailVerified &&
    name.length > 0 &&
    password.length >= 8 &&
    (!asProducer ||
      (bankCode.length > 0 &&
        bankName.length > 0 &&
        accountNumber.length > 0 &&
        accountHolder.length > 0));

  return (
    <div className="flex flex-1 items-center justify-center px-4 py-16">
      <div className="w-full max-w-md space-y-6">
        <div className="flex flex-col items-center gap-3 text-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-wb-green shadow-md">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              className="h-8 w-8 text-white"
              stroke="currentColor"
              strokeWidth={2}
            >
              <path
                d="M9 12.75 11.25 15 15 9.75"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              <path
                d="M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6l7-3z"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </div>
          <h1 className="text-2xl font-semibold">WellBuying</h1>
          <p className="text-sm text-wb-secondary">가격을 알면, 구매가 달라져요</p>
        </div>

        <div className="rounded-xl border border-wb-line bg-wb-surface p-6 shadow-sm">
          <div className="mb-5 grid grid-cols-2 gap-1 rounded-lg bg-wb-tag-surface p-1">
            {(["login", "signup"] as Mode[]).map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => {
                  setMode(m);
                  setError(null);
                  setNotice(null);
                }}
                className={`rounded-md py-2 text-sm font-semibold transition-colors ${
                  mode === m
                    ? "bg-wb-surface text-wb-ink shadow-sm"
                    : "text-wb-secondary"
                }`}
              >
                {m === "login" ? "로그인" : "회원가입"}
              </button>
            ))}
          </div>

          <div className="mb-4">
            <h2 className="text-lg font-semibold">
              {mode === "login" ? "다시 만나 반가워요" : "WellBuying을 시작해요"}
            </h2>
            <p className="text-xs text-wb-secondary">
              {mode === "login"
                ? "계정으로 로그인해주세요."
                : "이메일 인증 후 기본 정보를 입력해 계정을 만들어요."}
            </p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            {mode === "signup" && (
              <TextField
                label="이름 또는 상호명"
                placeholder="푸른살림 연구소"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
            )}

            {mode === "signup" ? (
              <div className="space-y-2">
                <div className="flex items-end gap-2">
                  <div className="flex-1">
                    <TextField
                      label="이메일"
                      type="email"
                      placeholder="name@example.com"
                      value={email}
                      disabled={emailVerified}
                      onChange={(e) => {
                        setEmail(e.target.value);
                        if (emailVerified || codeSent) resetEmailVerification();
                      }}
                      required
                    />
                  </div>
                  {!emailVerified && (
                    <Button
                      type="button"
                      variant="secondary"
                      className="h-11 shrink-0 px-4"
                      onClick={handleSendCode}
                      loading={sendingCode}
                    >
                      {codeSent ? "재전송" : "인증코드 발송"}
                    </Button>
                  )}
                </div>

                {emailVerified ? (
                  <p className="text-xs font-semibold text-wb-green">
                    이메일 인증이 완료됐어요.
                  </p>
                ) : (
                  codeSent && (
                    <div className="flex items-end gap-2">
                      <div className="flex-1">
                        <TextField
                          label="인증 코드"
                          placeholder="6자리 코드"
                          value={code}
                          onChange={(e) => setCode(e.target.value)}
                        />
                      </div>
                      <Button
                        type="button"
                        variant="secondary"
                        className="h-11 shrink-0 px-4"
                        onClick={handleVerifyCode}
                        loading={verifyingCode}
                        disabled={code.length === 0}
                      >
                        확인
                      </Button>
                    </div>
                  )
                )}
              </div>
            ) : (
              <TextField
                label="이메일"
                type="email"
                placeholder="name@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            )}

            <TextField
              label="비밀번호"
              type="password"
              placeholder={mode === "signup" ? "8자 이상 입력" : "비밀번호"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />

            {mode === "signup" && (
              <div className="rounded-lg border border-wb-line bg-wb-canvas p-3">
                <label className="flex cursor-pointer items-center justify-between gap-3">
                  <span>
                    <span className="block text-sm font-bold">생산자로 가입</span>
                    <span className="block text-xs text-wb-secondary">
                      공동구매를 직접 개설하고 정산을 확인할 수 있어요.
                    </span>
                  </span>
                  <input
                    type="checkbox"
                    className="h-5 w-5 accent-wb-green"
                    checked={asProducer}
                    onChange={(e) => setAsProducer(e.target.checked)}
                  />
                </label>

                {asProducer && (
                  <div className="mt-4 space-y-3 border-t border-wb-line pt-4">
                    <TextField
                      label="은행 코드"
                      placeholder="예: 004"
                      value={bankCode}
                      onChange={(e) => setBankCode(e.target.value)}
                      required
                    />
                    <TextField
                      label="은행명"
                      placeholder="예: KB국민은행"
                      value={bankName}
                      onChange={(e) => setBankName(e.target.value)}
                      required
                    />
                    <TextField
                      label="계좌번호"
                      value={accountNumber}
                      onChange={(e) => setAccountNumber(e.target.value)}
                      required
                    />
                    <TextField
                      label="예금주"
                      value={accountHolder}
                      onChange={(e) => setAccountHolder(e.target.value)}
                      required
                    />
                    <TextField
                      label="상호명 (선택)"
                      value={companyName}
                      onChange={(e) => setCompanyName(e.target.value)}
                    />
                  </div>
                )}
              </div>
            )}

            {error && <Banner tone="error">{error}</Banner>}
            {!error && notice && <Banner tone="success">{notice}</Banner>}

            <Button
              type="submit"
              className="w-full"
              loading={submitting}
              disabled={mode === "login" ? !canSubmitLogin : !canSubmitSignup}
            >
              {mode === "login" ? "로그인" : "회원가입"}
            </Button>
          </form>

          <div className="my-5 flex items-center gap-3">
            <div className="h-px flex-1 bg-wb-line" />
            <span className="text-xs text-wb-secondary">또는</span>
            <div className="h-px flex-1 bg-wb-line" />
          </div>

          <div className="space-y-2">
            {SOCIAL_PROVIDERS.map(({ provider, label }) => (
              <Button
                key={provider}
                type="button"
                variant="secondary"
                className="w-full"
                onClick={() => {
                  window.location.href = getOAuthAuthorizationUrl(provider);
                }}
              >
                {label}
              </Button>
            ))}
          </div>
        </div>

        <p className="text-center text-xs text-wb-secondary">1차 MVP · 회원 플로우</p>
      </div>
    </div>
  );
}
