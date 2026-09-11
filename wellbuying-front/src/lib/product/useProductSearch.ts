import { useEffect, useState } from "react";
import { searchProducts } from "@/lib/api/product";
import { ApiError } from "@/lib/api/http";
import type { ProductSearchResponse, SearchSortType } from "@/lib/api/types";

export function useProductSearch(
  keyword: string,
  options?: { sort?: SearchSortType; size?: number; activeGroupBuyOnly?: boolean },
) {
  const [items, setItems] = useState<ProductSearchResponse[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const sort = options?.sort ?? "RELEVANCE";
  const size = options?.size ?? 12;
  const activeGroupBuyOnly = options?.activeGroupBuyOnly ?? true;

  useEffect(() => {
    let ignore = false;

    async function loadFirst() {
      setLoading(true);
      setError(null);
      try {
        const result = await searchProducts({ keyword, sort, size, activeGroupBuyOnly });
        if (ignore) return;
        setItems(result.content);
        setCursor(result.nextCursor);
        setHasNext(result.hasNext);
      } catch (e) {
        if (!ignore) setError(e instanceof ApiError ? e.message : "검색 결과를 불러오지 못했어요.");
      } finally {
        if (!ignore) setLoading(false);
      }
    }

    if (keyword.trim().length > 0) {
      void loadFirst();
    } else {
      setItems([]);
      setCursor(null);
      setHasNext(false);
      setLoading(false);
    }

    return () => {
      ignore = true;
    };
  }, [keyword, sort, size, activeGroupBuyOnly]);

  async function loadMore() {
    if (!hasNext || !cursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const result = await searchProducts({ keyword, sort, size, cursor, activeGroupBuyOnly });
      setItems((prev) => [...prev, ...result.content]);
      setCursor(result.nextCursor);
      setHasNext(result.hasNext);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "검색 결과를 불러오지 못했어요.");
    } finally {
      setLoadingMore(false);
    }
  }

  return { items, loading, loadingMore, hasNext, error, loadMore };
}
