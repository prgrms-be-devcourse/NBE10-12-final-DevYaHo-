"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { Banner } from "@/components/ui/Banner";
import { registerBillingKey } from "@/lib/api/billingKey";
import { ApiError } from "@/lib/api/http";
import { clearPendingParticipation } from "@/lib/payments/pendingParticipation";

// 토스 결제창이 카드 입력을 마치고 돌아오는 지점.
// 성공이면 ?customerKey=..&authKey=.., 실패/취소면 ?code=..&message=..가 붙는다.
// 빌링키 교환에 성공하면 원래 보던 공동구매 상세로 되돌려보내고, 거기서 결제 창이 다시 열리며
// 참여를 이어가게 한다.
function CallbackBody() {
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const router = useRouter();
  const [message, setMessage] = useState<string | null>(null);
  // StrictMode는 effect를 두 번 실행한다. authKey는 1회용이라 두 번째 교환은 반드시 실패하므로 막는다
  const started = useRef(false);

  const dealPath = `/deals/${params.id}`;

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    const authKey = searchParams.get("authKey");
    const customerKey = searchParams.get("customerKey");

    if (!authKey || !customerKey) {
      // 취소하고 돌아온 경우 보관해 둔 참여 정보를 남겨둘 이유가 없다
      clearPendingParticipation();
      setMessage(searchParams.get("message") ?? "카드 인증이 취소되었거나 실패했어요.");
      return;
    }

    registerBillingKey(authKey, customerKey)
      .then(() => {
        // replace를 쓰는 이유: 뒤로 가기로 이 콜백에 다시 들어오면 이미 소진된 authKey로 재교환을 시도한다
        router.replace(dealPath);
      })
      .catch((e: unknown) => {
        clearPendingParticipation();
        setMessage(e instanceof ApiError ? e.message : "빌링키 발급에 실패했어요.");
      });
  }, [searchParams, router, dealPath]);

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 px-6 py-10">
      <h1 className="text-xl font-bold">카드 등록</h1>
      {message === null ? (
        <p className="text-sm text-wb-secondary">카드를 등록하는 중입니다...</p>
      ) : (
        <>
          <Banner tone="error">{message}</Banner>
          <Link href={dealPath} className="text-sm font-semibold text-wb-green underline">
            공동구매로 돌아가기
          </Link>
        </>
      )}
    </main>
  );
}

export default function DealBillingCallbackPage() {
  return (
    <Suspense fallback={<main className="px-6 py-10 text-sm text-wb-secondary">불러오는 중...</main>}>
      <CallbackBody />
    </Suspense>
  );
}
