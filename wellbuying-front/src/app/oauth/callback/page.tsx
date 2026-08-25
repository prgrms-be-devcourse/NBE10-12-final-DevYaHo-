"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Card } from "@/components/ui/Card";
import { Banner } from "@/components/ui/Banner";
import { useAuth } from "@/lib/auth/AuthProvider";
import { exchangeOAuthCode } from "@/lib/api/auth";
import { ApiError } from "@/lib/api/http";

// 백엔드 OAuth2AuthenticationSuccessHandler/FailureHandler가 공유하는 단일 리다이렉트 목적지.
// 신규 로그인은 ?code=, 로그인 상태의 추가 연동은 ?linked=true&provider=, 실패는 ?error= 로 구분해서 보낸다.
function OAuthCallbackContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const { refreshMember } = useAuth();
  const [exchangeError, setExchangeError] = useState<string | null>(null);

  const code = searchParams.get("code");
  const linked = searchParams.get("linked") === "true";
  const provider = searchParams.get("provider");
  const failed = searchParams.get("error") !== null;

  useEffect(() => {
    if (failed || linked || !code) return;
    exchangeOAuthCode(code)
      .then(async () => {
        await refreshMember();
        router.replace("/home");
      })
      .catch((e) => {
        setExchangeError(e instanceof ApiError ? e.message : "소셜 로그인 처리 중 오류가 발생했어요.");
      });
  }, [code, failed, linked, refreshMember, router]);

  useEffect(() => {
    if (linked) router.replace(`/profile?linked=true&provider=${provider ?? ""}`);
  }, [linked, provider, router]);

  const error = failed
    ? "소셜 로그인에 실패했어요. 다시 시도해주세요."
    : !code && !linked
      ? "잘못된 접근이에요."
      : exchangeError;

  return (
    <div className="mx-auto flex w-full max-w-md flex-1 items-center justify-center px-4 py-16">
      <Card className="w-full space-y-4 text-center">
        {error ? (
          <>
            <Banner tone="error">{error}</Banner>
            <a href="/login" className="text-sm font-semibold text-wb-green">
              로그인으로 돌아가기
            </a>
          </>
        ) : (
          <p className="text-sm text-wb-secondary">로그인 처리 중이에요...</p>
        )}
      </Card>
    </div>
  );
}

export default function OAuthCallbackPage() {
  return (
    <Suspense
      fallback={
        <div className="flex flex-1 items-center justify-center py-16 text-sm text-wb-secondary">
          로딩 중...
        </div>
      }
    >
      <OAuthCallbackContent />
    </Suspense>
  );
}
