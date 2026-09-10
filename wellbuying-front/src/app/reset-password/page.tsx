"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { Banner } from "@/components/ui/Banner";
import { ApiError } from "@/lib/api/http";
import { resetPassword, sendPasswordReissueCode, verifyPasswordReissueCode } from "@/lib/api/auth";

type Step = "email" | "code" | "reset" | "done";

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function ResetPasswordPage() {
  const router = useRouter();
  const [step, setStep] = useState<Step>("email");

  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [newPasswordConfirm, setNewPasswordConfirm] = useState("");

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSendCode(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!EMAIL_PATTERN.test(email)) {
      setError("올바른 이메일 형식을 입력해주세요.");
      return;
    }
    setSubmitting(true);
    try {
      await sendPasswordReissueCode(email);
      setStep("code");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "인증 코드 발송에 실패했어요.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleVerifyCode(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await verifyPasswordReissueCode(email, code);
      setStep("reset");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "인증 코드가 올바르지 않아요.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleResetPassword(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (newPassword.length < 8) {
      setError("비밀번호는 8자 이상 입력해주세요.");
      return;
    }
    if (newPassword !== newPasswordConfirm) {
      setError("비밀번호가 일치하지 않아요.");
      return;
    }
    setSubmitting(true);
    try {
      await resetPassword(email, newPassword);
      setStep("done");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "비밀번호 재설정에 실패했어요.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-1 items-center justify-center px-4 py-16">
      <div className="w-full max-w-md space-y-6">
        <div className="flex flex-col items-center gap-3 text-center">
          <h1 className="text-2xl font-semibold">비밀번호 재설정</h1>
          <p className="text-sm text-wb-secondary">
            {step === "email" && "가입한 이메일로 인증 코드를 보내드려요."}
            {step === "code" && "이메일로 받은 인증 코드를 입력해주세요."}
            {step === "reset" && "새로운 비밀번호를 설정해주세요."}
            {step === "done" && "비밀번호가 변경됐어요."}
          </p>
        </div>

        <div className="rounded-xl border border-wb-line bg-wb-surface p-6 shadow-sm">
          {error && (
            <div className="mb-4">
              <Banner tone="error">{error}</Banner>
            </div>
          )}

          {step === "email" && (
            <form onSubmit={handleSendCode} className="space-y-4">
              <TextField
                label="이메일"
                type="email"
                placeholder="name@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
              <Button type="submit" className="w-full" loading={submitting} disabled={email.length === 0}>
                인증 코드 받기
              </Button>
            </form>
          )}

          {step === "code" && (
            <form onSubmit={handleVerifyCode} className="space-y-4">
              <TextField
                label="인증 코드"
                placeholder="6자리 코드"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                required
              />
              <Button type="submit" className="w-full" loading={submitting} disabled={code.length === 0}>
                확인
              </Button>
            </form>
          )}

          {step === "reset" && (
            <form onSubmit={handleResetPassword} className="space-y-4">
              <TextField
                label="새 비밀번호"
                type="password"
                placeholder="8자 이상 입력"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                required
              />
              <TextField
                label="새 비밀번호 확인"
                type="password"
                placeholder="비밀번호 재입력"
                value={newPasswordConfirm}
                onChange={(e) => setNewPasswordConfirm(e.target.value)}
                required
              />
              <Button
                type="submit"
                className="w-full"
                loading={submitting}
                disabled={newPassword.length === 0 || newPasswordConfirm.length === 0}
              >
                비밀번호 변경
              </Button>
            </form>
          )}

          {step === "done" && (
            <div className="space-y-4 text-center">
              <Banner tone="success">비밀번호가 성공적으로 변경됐어요.</Banner>
              <Button className="w-full" onClick={() => router.push("/login")}>
                로그인하러 가기
              </Button>
            </div>
          )}

          {step !== "done" && (
            <p className="mt-6 text-center text-sm text-wb-secondary">
              <Link href="/login" className="font-bold text-wb-green hover:underline">
                로그인으로 돌아가기
              </Link>
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
