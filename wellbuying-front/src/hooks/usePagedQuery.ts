"use client";

import { useEffect, useState } from "react";
import { ApiError } from "@/lib/api/http";

// 모듈 레벨 캐시 - 탭 전환 등으로 컴포넌트가 리마운트돼도 유지되고, 새로고침하면 초기화된다.
// 페이지 1 -> 2 -> 1 처럼 같은 조건으로 돌아왔을 때 불필요한 재요청을 건너뛰기 위한 용도.
const cache = new Map<string, unknown>();

// 목록이 변경되는 액션(승인/반려/삭제 등) 이후 호출해서 해당 목록의 캐시를 전부 비운다.
export function invalidatePagedQuery(namespace: string) {
  for (const key of cache.keys()) {
    if (key.startsWith(`${namespace}:`)) cache.delete(key);
  }
}

export function usePagedQuery<T>(
  namespace: string,
  params: Record<string, unknown>,
  fetcher: () => Promise<T>,
  fallbackMessage = "목록을 불러오지 못했어요.",
): { data: T | null; error: string | null; loading: boolean } {
  const cacheKey = `${namespace}:${JSON.stringify(params)}`;
  const cached = cache.get(cacheKey) as T | undefined;

  const [data, setData] = useState<T | null>(cached ?? null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(cached === undefined);

  useEffect(() => {
    const hit = cache.get(cacheKey) as T | undefined;
    if (hit !== undefined) {
      setData(hit);
      setError(null);
      setLoading(false);
      return;
    }

    let ignore = false;
    setLoading(true);
    setError(null);

    fetcher()
      .then((res) => {
        if (ignore) return;
        cache.set(cacheKey, res);
        setData(res);
      })
      .catch((e) => {
        if (ignore) return;
        setError(e instanceof ApiError ? e.message : fallbackMessage);
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
    // cacheKey가 params를 온전히 반영하므로 이걸로만 재조회 여부를 판단한다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cacheKey]);

  return { data, error, loading };
}
