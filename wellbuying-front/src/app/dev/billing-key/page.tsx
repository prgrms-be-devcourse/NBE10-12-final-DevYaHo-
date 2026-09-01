"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { ApiError } from "@/lib/api/http";
import {
  deleteBillingKey,
  getBillingKey,
  requestBillingKeyCustomerKey,
} from "@/lib/api/billingKey";
import type { BillingKeyResponse } from "@/lib/api/types";
import { getAccessToken } from "@/lib/auth/token-storage";
import { API_BASE_URL } from "@/lib/api/http";
import { loadTossPayments } from "@/lib/toss/loadTossPayments";

// 결제 모듈 검증용 임시 화면 (05-billingkey-issue.md 확인용).
// 참여 플로우에 붙는 정식 카드 등록 UI는 06-billingkey-registration-ui.md에서 따로 만들며,
// 그때 이 페이지는 삭제한다.
export default function DevBillingKeyPage() {
  const [billingKey, setBillingKey] = useState<BillingKeyResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  // 401이 뜰 때 원인을 화면에서 바로 가릴 수 있게 모아둔다 (개발용 화면이라 노출해도 되는 값만 담는다)
  const [diagnostics, setDiagnostics] = useState<{ origin: string; hasToken: boolean } | null>(null);

  const clientKey = process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY;

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setBillingKey(await getBillingKey());
    } catch (e) {
      // 401은 원인이 둘로 갈린다 - 토큰이 아예 없는 경우와, 있는데 거부된 경우.
      // 화면에서 바로 구분되게 문구를 나눈다
      if (e instanceof ApiError && e.status === 401) {
        setError(
          getAccessToken()
            ? "토큰은 있는데 서버가 401을 반환했습니다. 만료된 토큰일 수 있으니 다시 로그인해보세요."
            : "이 브라우저 오리진에 로그인 토큰이 없습니다. 먼저 로그인하세요.",
        );
      } else {
        setError(e instanceof ApiError ? e.message : "빌링키 조회에 실패했습니다.");
      }
      setBillingKey(null);
    } finally {
      setDiagnostics({ origin: window.location.origin, hasToken: getAccessToken() !== null });
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  async function handleRegister() {
    if (!clientKey) {
      setError("NEXT_PUBLIC_TOSS_CLIENT_KEY가 설정되지 않았습니다.");
      return;
    }
    setWorking(true);
    setError(null);
    setNotice(null);
    try {
      // ① 서버에서 customerKey를 받는다. 발급 때 쓴 값과 승인 때 보내는 값이 같아야 하므로
      //    프런트가 임의로 만들지 않고 반드시 서버 값을 쓴다
      const { customerKey } = await requestBillingKeyCustomerKey();

      // ② 토스 결제창을 띄운다. 카드번호는 이 창 안에서만 입력되며 우리 코드가 관여하지 않는다
      const toss = await loadTossPayments(clientKey);
      const origin = window.location.origin;
      await toss.requestBillingAuth("카드", {
        customerKey,
        successUrl: `${origin}/dev/billing-key/callback`,
        failUrl: `${origin}/dev/billing-key/callback`,
      });
      // 성공 시 successUrl로 리다이렉트되므로 이 아래는 실행되지 않는다
    } catch (e) {
      // 사용자가 결제창을 닫아도 여기로 들어온다
      setError(e instanceof Error ? e.message : "카드 등록 창을 여는 데 실패했습니다.");
      setWorking(false);
    }
  }

  async function handleDelete() {
    setWorking(true);
    setError(null);
    setNotice(null);
    try {
      await deleteBillingKey();
      setNotice("빌링키를 폐기했습니다.");
      await reload();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "폐기에 실패했습니다.");
    } finally {
      setWorking(false);
    }
  }

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-5 px-5 py-10">
      <header className="flex flex-col gap-1">
        <h1 className="text-xl font-bold text-wb-ink">빌링키 등록 (개발용)</h1>
        <p className="text-sm text-wb-ink/60">
          결제 모듈 검증용 임시 화면입니다. 로그인한 상태에서 사용하세요.
        </p>
      </header>

      {error && (
        <Banner>
          {error}{" "}
          <Link href="/login" className="font-semibold underline">
            로그인하러 가기
          </Link>
        </Banner>
      )}
      {notice && <Banner tone="success">{notice}</Banner>}

      <section className="rounded-xl border border-wb-line bg-wb-surface p-5">
        <h2 className="mb-3 text-sm font-semibold text-wb-ink">등록 상태</h2>
        {loading ? (
          <p className="text-sm text-wb-ink/60">불러오는 중...</p>
        ) : billingKey?.registered ? (
          <p className="text-sm text-wb-ink">
            등록됨 — {billingKey.cardCompany ?? "카드사 정보 없음"}{" "}
            {billingKey.cardLast4 ? `****${billingKey.cardLast4}` : ""}
          </p>
        ) : (
          <p className="text-sm text-wb-ink/60">등록된 카드가 없습니다.</p>
        )}
      </section>

      <div className="flex gap-2">
        <Button onClick={handleRegister} loading={working} disabled={loading}>
          {billingKey?.registered ? "카드 교체" : "카드 등록"}
        </Button>
        {billingKey?.registered && (
          <Button variant="secondary" onClick={handleDelete} loading={working}>
            폐기
          </Button>
        )}
      </div>

      {diagnostics && (
        <section className="rounded-xl border border-wb-line bg-wb-surface p-4 text-xs text-wb-ink/70">
          <h2 className="mb-2 font-semibold text-wb-ink">진단</h2>
          <ul className="flex flex-col gap-1">
            <li>
              현재 오리진: <code>{diagnostics.origin}</code>
              {diagnostics.origin !== "http://localhost:3000" && (
                <strong className="ml-1 text-red-600">
                  ← localStorage는 오리진마다 분리됩니다. localhost:3000으로 접속하세요
                </strong>
              )}
            </li>
            <li>
              저장된 액세스 토큰: <code>{diagnostics.hasToken ? "있음" : "없음"}</code>
            </li>
            <li>
              API 서버: <code>{API_BASE_URL}</code>
            </li>
            <li>
              토스 클라이언트 키: <code>{clientKey ? "설정됨" : "없음"}</code>
            </li>
          </ul>
        </section>
      )}

      <p className="text-xs text-wb-ink/50">
        테스트 카드 번호는 토스페이먼츠 개발자센터 문서를 참고하세요. 테스트 키를 쓰므로 실제 출금은
        발생하지 않습니다.
      </p>
    </main>
  );
}
