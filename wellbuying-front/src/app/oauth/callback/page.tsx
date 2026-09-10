"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Card } from "@/components/ui/Card";
import { Banner } from "@/components/ui/Banner";
import { useAuth } from "@/lib/auth/AuthProvider";
import { exchangeOAuthCode } from "@/lib/api/auth";
import { ApiError } from "@/lib/api/http";

// 백엔드 OAuth2AuthenticationFailureHandler가 error 쿼리 파라미터로 실어 보내는 코드 → 한글 메시지.
// 목록에 없는 코드는 아래 기본 문구로 폴백한다.
const OAUTH_ERROR_MESSAGES: Record<string, string> = {
  SOCIAL_409_EMAIL_EXISTS:
    "이미 가입된 이메일이에요. 이메일/비밀번호로 로그인한 뒤 마이페이지에서 연동해주세요.",
  SOCIAL_409_ALREADY_LINKED: "이미 연동된 소셜 계정이에요.",
  MEMBER_403_DORMANT: "휴면 처리된 계정이에요. 이메일 인증 후 재활성화해주세요.",
  email_required: "이메일 제공에 동의해야 로그인할 수 있어요.",
};
const DEFAULT_OAUTH_ERROR_MESSAGE = "소셜 로그인에 실패했어요. 다시 시도해주세요.";

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
  const errorCode = searchParams.get("error");
  const failed = errorCode !== null;

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
    ? (errorCode && OAUTH_ERROR_MESSAGES[errorCode]) || DEFAULT_OAUTH_ERROR_MESSAGE
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
