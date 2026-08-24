"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { RequireAuth } from "@/components/auth/RequireAuth";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { TextField } from "@/components/ui/TextField";
import { Banner } from "@/components/ui/Banner";
import { useAuth } from "@/lib/auth/AuthProvider";
import { sellerApply } from "@/lib/api/auth";
import { ApiError } from "@/lib/api/http";

function SellerApplyContent() {
  const { member } = useAuth();
  const router = useRouter();

  const [bankCode, setBankCode] = useState("");
  const [bankName, setBankName] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [accountHolder, setAccountHolder] = useState("");
  const [companyName, setCompanyName] = useState("");

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await sellerApply({
        bankCode,
        bankName,
        accountNumber,
        accountHolder,
        companyName: companyName || undefined,
      });
      setDone(true);
    } catch (e) {
      setError(
        e instanceof ApiError
          ? e.message
          : "신청 처리 중 오류가 발생했어요.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  if (member?.role !== "BUYER" && !done) {
    return (
      <div className="mx-auto w-full max-w-md px-4 py-16">
        <Card>
          <p className="text-sm text-wb-secondary">
            이미 생산자이거나 신청할 수 없는 역할이에요.
          </p>
        </Card>
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-md px-4 py-16">
      <Card className="space-y-5">
        <div>
          <h1 className="text-xl font-semibold">생산자 신청</h1>
          <p className="text-xs text-wb-secondary">
            정산에 사용할 계좌 정보를 입력해주세요.
          </p>
        </div>

        {done ? (
          <Banner tone="success">
            신청이 접수됐어요. 관리자 승인 후 생산자 권한이 부여돼요.
          </Banner>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
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

            {error && <Banner tone="error">{error}</Banner>}

            <Button type="submit" className="w-full" loading={submitting}>
              신청하기
            </Button>
          </form>
        )}

        <Button variant="secondary" className="w-full" onClick={() => router.push("/profile")}>
          내 정보로 돌아가기
        </Button>
      </Card>
    </div>
  );
}

export default function SellerApplyPage() {
  return (
    <RequireAuth>
      <SellerApplyContent />
    </RequireAuth>
  );
}
