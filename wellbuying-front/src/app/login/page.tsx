"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ShoppingBag } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { SelectField } from "@/components/ui/SelectField";
import { Banner } from "@/components/ui/Banner";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ApiError } from "@/lib/api/http";
import { BANK_OPTIONS, bankNameByCode } from "@/lib/constants/banks";
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

type Mode = "login" | "signup-select" | "signup";

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function LoginPage() {
  const [mode, setMode] = useState<Mode>("login");
  const router = useRouter();
  const { refreshMember } = useAuth();

  const [email, setEmail] = useState("");

  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
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
  const [resendCooldown, setResendCooldown] = useState(0);
  const [sendCodeBlocked, setSendCodeBlocked] = useState(false);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  function resetEmailVerification() {
    setEmailVerified(false);
    setCodeSent(false);
    setCode("");
    setResendCooldown(0);
  }

  useEffect(() => {
    if (resendCooldown <= 0) return;
    const timer = setTimeout(() => setResendCooldown((prev) => prev - 1), 1000);
    return () => clearTimeout(timer);
  }, [resendCooldown]);

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
      setResendCooldown(30);
      setNotice("인증 코드를 이메일로 보냈어요.");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "인증 코드 발송에 실패했어요.");
      setSendCodeBlocked(true);
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

  function startSignup(producer: boolean) {
    setAsProducer(producer);
    setMode("signup");
    setError(null);
    setNotice(null);
  }

  async function handleLogin() {
    await login(email, password);
    await refreshMember();
    router.push("/");
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
    router.push("/");
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
    password === passwordConfirm &&
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
            <ShoppingBag className="h-8 w-8 text-white" strokeWidth={2} />
          </div>
          <h1 className="text-2xl font-semibold">WellBuying</h1>
          <p className="text-sm text-wb-secondary">가격을 알면, 구매가 달라져요</p>
        </div>

        <div className="rounded-xl border border-wb-line bg-wb-surface p-6 shadow-sm">
          <div className="mb-4">
            <h2 className="text-lg font-semibold">
              {mode === "login" && "다시 만나 반가워요"}
              {mode === "signup-select" && "WellBuying을 시작해요"}
              {mode === "signup" && (asProducer ? "생산자로 시작해요" : "WellBuying을 시작해요")}
            </h2>
            <p className="text-xs text-wb-secondary">
              {mode === "login" && "계정으로 로그인해주세요."}
              {mode === "signup-select" && "가입 유형을 선택해주세요."}
              {mode === "signup" && "이메일 인증 후 기본 정보를 입력해 계정을 만들어요."}
            </p>
          </div>

          {mode === "signup-select" ? (
            <div className="space-y-3">
              <button
                type="button"
                onClick={() => startSignup(false)}
                className="w-full rounded-lg border border-wb-line bg-wb-canvas p-4 text-left transition-colors hover:border-wb-green"
              >
                <span className="block text-sm font-bold">일반 회원으로 가입</span>
                <span className="block text-xs text-wb-secondary">
                  공동구매에 참여하고 특가를 만나보세요.
                </span>
              </button>
              <button
                type="button"
                onClick={() => startSignup(true)}
                className="w-full rounded-lg border border-wb-line bg-wb-canvas p-4 text-left transition-colors hover:border-wb-green"
              >
                <span className="block text-sm font-bold">생산자로 가입</span>
                <span className="block text-xs text-wb-secondary">
                  공동구매를 직접 개설하고 정산을 확인할 수 있어요.
                </span>
              </button>
            </div>
          ) : (
            <>
          <form onSubmit={handleSubmit} className="space-y-4">
            {mode === "signup" && (
              <TextField
                label="이름"
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
                        setSendCodeBlocked(false);
                        if (emailVerified || codeSent) resetEmailVerification();
                      }}
                      required
                    />
                  </div>
                  {!emailVerified && !codeSent && (
                    <Button
                      type="button"
                      variant="secondary"
                      className="h-11 shrink-0 px-4"
                      onClick={handleSendCode}
                      loading={sendingCode}
                      disabled={sendCodeBlocked}
                    >
                      인증코드 발송
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
                        onClick={handleSendCode}
                        loading={sendingCode}
                        disabled={resendCooldown > 0}
                      >
                        {resendCooldown > 0 ? `재전송 (${resendCooldown}초)` : "재전송"}
                      </Button>
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
              <TextField
                label="비밀번호 확인"
                type="password"
                placeholder="비밀번호 재입력"
                value={passwordConfirm}
                onChange={(e) => setPasswordConfirm(e.target.value)}
                required
              />
            )}
            {mode === "login" && (
              <div className="text-right">
                <Link href="/reset-password" className="text-xs font-semibold text-wb-secondary hover:text-wb-green hover:underline">
                  비밀번호를 잊으셨나요?
                </Link>
              </div>
            )}

            {mode === "signup" && asProducer && (
              <div className="space-y-3">
                <SelectField
                  label="은행"
                  placeholder="은행을 선택해주세요"
                  value={bankCode}
                  onChange={(code) => {
                    setBankCode(code);
                    setBankName(bankNameByCode(code));
                  }}
                  options={BANK_OPTIONS}
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

            {error && <Banner tone="error">{error}</Banner>}
            {!error && notice && <Banner tone="success">{notice}</Banner>}

            {mode === "signup" && (
              <div className="flex items-center justify-between rounded-lg bg-wb-canvas px-3 py-2">
                <span className="text-xs font-bold text-wb-secondary">
                  {asProducer ? "생산자 회원가입" : "일반 회원가입"}
                </span>
                <button
                  type="button"
                  onClick={() => setMode("signup-select")}
                  className="text-xs font-semibold text-wb-green hover:underline"
                >
                  가입 유형 변경
                </button>
              </div>
            )}

            <Button
              type="submit"
              className="w-full"
              loading={submitting}
              disabled={mode === "login" ? !canSubmitLogin : !canSubmitSignup}
            >
              {mode === "login" ? "로그인" : "회원가입"}
            </Button>
          </form>

          {mode === "login" && (
            <>
              <div className="my-5 flex items-center gap-3">
                <div className="h-px flex-1 bg-wb-line" />
                <span className="text-xs text-wb-secondary">또는</span>
                <div className="h-px flex-1 bg-wb-line" />
              </div>

              <div className="flex justify-center gap-4">
                {SOCIAL_PROVIDERS.map(({ provider, label }) => (
                  <button
                key={provider}
                type="button"
                onClick={() => {
                  window.location.href = getOAuthAuthorizationUrl(provider);
                }}
                className={`flex h-12 w-12 items-center justify-center rounded-full text-xs font-bold shadow-sm transition-opacity hover:opacity-80 ${
                  provider === "KAKAO" ? "bg-[#FEE500] text-black" : "border border-wb-line bg-white text-wb-ink"
                }`}
                aria-label={label}
              >
                {provider === "KAKAO" ? (<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" className="h-6 w-6" fill="#000000"><path d="M12 3c-5.5 0-10 3.5-10 7.8 0 2.8 1.8 5.3 4.5 6.7-.2.8-.7 2.6-.8 2.8-.1.3.1.4.3.3.3-.2 3.1-2 4.4-2.9.5.1 1.1.2 1.6.2 5.5 0 10-3.5 10-7.8S17.5 3 12 3z"/></svg>) : (<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" className="h-6 w-6"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.7 17.74 9.5 24 9.5z"/><path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/><path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/><path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.2-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/><path fill="none" d="M0 0h48v48H0z"/></svg>)}
              </button>
                ))}
              </div>
            </>
          )}
          </>
          )}

          <div className="mt-8 text-center text-sm text-wb-secondary">
            {mode === "login" ? (
              <>
                아직 계정이 없으신가요?{" "}
                <button
                  type="button"
                  onClick={() => {
                    setMode("signup-select");
                    setError(null);
                    setNotice(null);
                  }}
                  className="font-bold text-wb-green hover:underline"
                >
                  회원가입하기
                </button>
              </>
            ) : (
              <>
                이미 계정이 있으신가요?{" "}
                <button
                  type="button"
                  onClick={() => {
                    setMode("login");
                    setError(null);
                    setNotice(null);
                  }}
                  className="font-bold text-wb-green hover:underline"
                >
                  로그인하기
                </button>
              </>
            )}
          </div>
        </div>

      </div>
    </div>
  );
}
