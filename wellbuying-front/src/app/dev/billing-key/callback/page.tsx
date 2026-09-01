"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Banner } from "@/components/ui/Banner";
import { ApiError } from "@/lib/api/http";
import { registerBillingKey } from "@/lib/api/billingKey";

type State = "working" | "done" | "failed";

// 토스 결제창이 돌려보내는 지점. 성공이면 ?customerKey=..&authKey=.., 실패면 ?code=..&message=..가 붙는다
function CallbackBody() {
  const params = useSearchParams();
  const [state, setState] = useState<State>("working");
  const [message, setMessage] = useState("빌링키를 발급받는 중입니다...");
  // React 18 StrictMode는 effect를 두 번 실행한다. authKey는 1회용이라 두 번째 교환은 반드시 실패하므로 막는다
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    const authKey = params.get("authKey");
    const customerKey = params.get("customerKey");

    if (!authKey || !customerKey) {
      setState("failed");
      setMessage(params.get("message") ?? "카드 인증이 취소되었거나 실패했습니다.");
      return;
    }

    registerBillingKey(authKey, customerKey)
      .then((result) => {
        setState("done");
        setMessage(
          `등록 완료 — ${result.cardCompany ?? "카드사 정보 없음"} ${
            result.cardLast4 ? `****${result.cardLast4}` : ""
          }`,
        );
      })
      .catch((e: unknown) => {
        setState("failed");
        setMessage(e instanceof ApiError ? e.message : "빌링키 발급에 실패했습니다.");
      });
  }, [params]);

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 px-5 py-10">
      <h1 className="text-xl font-bold text-wb-ink">카드 등록 결과</h1>
      {state === "working" ? (
        <p className="text-sm text-wb-ink/60">{message}</p>
      ) : (
        <Banner tone={state === "done" ? "success" : "error"}>{message}</Banner>
      )}
      <Link href="/dev/billing-key" className="text-sm font-semibold text-wb-green underline">
        등록 화면으로 돌아가기
      </Link>
    </main>
  );
}

export default function DevBillingKeyCallbackPage() {
  return (
    <Suspense fallback={<main className="px-5 py-10 text-sm">불러오는 중...</main>}>
      <CallbackBody />
    </Suspense>
  );
}
