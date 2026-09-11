import { useEffect, useState } from "react";
import { getProducts } from "@/lib/api/product";
import { ApiError } from "@/lib/api/http";
import type { ProductSortType, ProductSummaryResponse } from "@/lib/api/types";

export function useProductList(options?: { category?: number | null; sort?: ProductSortType; size?: number }) {
  const [items, setItems] = useState<ProductSummaryResponse[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const category = options?.category ?? undefined;
  const sort = options?.sort ?? "LATEST";
  const size = options?.size ?? 20;

  useEffect(() => {
    let ignore = false;

    async function loadFirst() {
      setLoading(true);
      setError(null);
      try {
        const result = await getProducts({ category: category ?? undefined, sort, size });
        if (ignore) return;
        setItems(result.content);
        setCursor(result.nextCursor);
        setHasNext(result.hasNext);
      } catch (e) {
        if (!ignore) setError(e instanceof ApiError ? e.message : "상품 목록을 불러오지 못했어요.");
      } finally {
        if (!ignore) setLoading(false);
      }
    }

    void loadFirst();
    return () => {
      ignore = true;
    };
  }, [category, sort, size]);

  async function loadMore() {
    if (!hasNext || !cursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const result = await getProducts({ category: category ?? undefined, sort, size, cursor });
      setItems((prev) => [...prev, ...result.content]);
      setCursor(result.nextCursor);
      setHasNext(result.hasNext);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "상품 목록을 불러오지 못했어요.");
    } finally {
      setLoadingMore(false);
    }
  }

  return { items, loading, loadingMore, hasNext, error, loadMore };
}