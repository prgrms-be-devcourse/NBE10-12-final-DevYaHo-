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
  const initialHit = cache.get(cacheKey) as T | undefined;

  const [resolvedKey, setResolvedKey] = useState(cacheKey);
  const [data, setData] = useState<T | null>(initialHit ?? null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(initialHit === undefined);

  // cacheKey(params)가 바뀌면 렌더 중에 캐시부터 즉시 반영한다 - effect 안에서 동기적으로
  // setState하면 불필요한 리렌더가 생기므로, React가 공식적으로 권장하는 "렌더 중 상태 조정" 패턴을 쓴다.
  if (cacheKey !== resolvedKey) {
    setResolvedKey(cacheKey);
    const hit = cache.get(cacheKey) as T | undefined;
    setData(hit ?? null);
    setError(null);
    setLoading(hit === undefined);
  }

  useEffect(() => {
    if (cache.has(cacheKey)) return; // 캐시 hit은 위에서 이미 렌더 중에 반영 완료 - 재요청 불필요

    let ignore = false;

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
