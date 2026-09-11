"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Search } from "lucide-react";
import { DealsSubNav } from "@/components/consumer/DealsSubNav";
import { GroupBuyCard } from "@/components/deal/GroupBuyCard";
import { ProductCard } from "@/components/deal/ProductCard";
import { ProductSearchCard } from "@/components/deal/ProductSearchCard";
import { Button } from "@/components/ui/Button";
import { listCategories } from "@/lib/api/category";
import { CATALOG_CATEGORIES } from "@/lib/groupBuy/seedCatalog";
import { useGroupBuyList } from "@/lib/groupBuy/useGroupBuyList";
import { useProductList } from "@/lib/product/useProductList";
import { useProductSearch } from "@/lib/product/useProductSearch";
import type { CategoryTreeResponse, ProductSortType, SearchSortType } from "@/lib/api/types";

type Sort = "popular" | "new" | "closing";

const SORT_LABEL: Record<Sort, string> = {
  popular: "인기순",
  new: "신상품순",
  closing: "마감 임박순",
};

const SORT_PARAM: Record<Sort, string> = {
  popular: "viewCount,desc",
  new: "createdAt,desc",
  closing: "endAt,asc",
};

const SEARCH_SORT_LABEL: Record<SearchSortType, string> = {
  RELEVANCE: "관련도순",
  POPULAR: "인기순",
};

const PRODUCT_SORT_LABEL: Record<ProductSortType, string> = {
  LATEST: "최신순",
  POPULAR: "인기순",
  PRICE_ASC: "가격 낮은순",
  PRICE_DESC: "가격 높은순",
};

const PAGE_SIZE = 12;
const PRODUCT_ROW_SIZE = 4;

function isSort(value: string | null): value is Sort {
  return !!value && value in SORT_LABEL;
}

function isSearchSort(value: string | null): value is SearchSortType {
  return !!value && value in SEARCH_SORT_LABEL;
}

export default function ExplorePage() {
  const searchParams = useSearchParams();
  const scheduledView = searchParams.get("status") === "scheduled";
  const isProductsView = searchParams.get("view") === "products";

  const [query, setQuery] = useState(() => searchParams.get("q") ?? "");
  const [category, setCategory] = useState("전체");
  const [sort, setSort] = useState<Sort>(() => {
    const param = searchParams.get("sort");
    return isSort(param) ? param : "popular";
  });
  const [searchSort, setSearchSort] = useState<SearchSortType>(() => {
    const param = searchParams.get("sort");
    return isSearchSort(param) ? param : "RELEVANCE";
  });
  const [productSort, setProductSort] = useState<ProductSortType>("LATEST");
  const [productParentCategoryId, setProductParentCategoryId] = useState<number | null>(null);
  const [productSubCategoryId, setProductSubCategoryId] = useState<number | null>(null);
  const [productCategories, setProductCategories] = useState<CategoryTreeResponse[]>([]);

  const productCategoryId = productSubCategoryId ?? productParentCategoryId;
  const selectedProductParent = productCategories.find((c) => c.id === productParentCategoryId) ?? null;
  const productSubCategories = selectedProductParent?.children ?? [];

  useEffect(() => {
    if (!isProductsView) return;
    let ignore = false;
    listCategories()
      .then((tree) => {
        if (!ignore) setProductCategories(tree);
      })
      .catch(() => {
        if (!ignore) setProductCategories([]);
      });
    return () => {
      ignore = true;
    };
  }, [isProductsView]);

  const {
    items: productListItems,
    loading: productListLoading,
    loadingMore: productListLoadingMore,
    hasNext: productListHasNext,
    error: productListError,
    loadMore: loadMoreProductList,
  } = useProductList({ category: productCategoryId, sort: productSort, size: 20 });

  const productListSentinelRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!isProductsView) return;
    const el = productListSentinelRef.current;
    if (!el) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting && productListHasNext && !productListLoadingMore) {
          void loadMoreProductList();
        }
      },
      { rootMargin: "200px" },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [isProductsView, productListHasNext, productListLoadingMore, loadMoreProductList]);

  const isSearchMode = query.trim().length > 0;

  const sortParam = SORT_PARAM[sort];
  const {
    items: ongoing,
    loading: ongoingLoading,
    page: ongoingPage,
    totalPages: ongoingTotalPages,
    setPage: setOngoingPage,
  } = useGroupBuyList("ONGOING", { sort: sortParam, size: PAGE_SIZE });
  const {
    items: scheduled,
    loading: scheduledLoading,
    page: scheduledPage,
    totalPages: scheduledTotalPages,
    setPage: setScheduledPage,
  } = useGroupBuyList("READY", { sort: sortParam, size: PAGE_SIZE });

  // 검색 모드에서는 같은 검색 API를 필터만 다르게 두 번 호출한다 - 위는 전체 상품, 아래는 그중
  // 지금 공동구매 진행 중인 것만. GroupBuy.title 같은 문구는 검색 인덱스에 없어서 이 방법이 최선이다.
  const {
    items: productResults,
    loading: productsLoading,
    loadingMore: productsLoadingMore,
    hasNext: productsHasNext,
    error: productsError,
    loadMore: loadMoreProducts,
  } = useProductSearch(query, { sort: searchSort, size: PRODUCT_ROW_SIZE, activeGroupBuyOnly: false });

  const {
    items: groupBuyResults,
    loading: groupBuyLoading,
    loadingMore: groupBuyLoadingMore,
    hasNext: groupBuyHasNext,
    error: groupBuyError,
    loadMore: loadMoreGroupBuyResults,
  } = useProductSearch(query, { sort: searchSort, size: PAGE_SIZE, activeGroupBuyOnly: true });

  const loading = scheduledView ? scheduledLoading : ongoingLoading;
  const page = scheduledView ? scheduledPage : ongoingPage;
  const totalPages = scheduledView ? scheduledTotalPages : ongoingTotalPages;
  const setPage = scheduledView ? setScheduledPage : setOngoingPage;

  useEffect(() => {
    setQuery(searchParams.get("q") ?? "");
    const param = searchParams.get("sort");
    if (isSort(param)) setSort(param);
    if (isSearchSort(param)) setSearchSort(param);
  }, [searchParams]);

  useEffect(() => {
    setOngoingPage(0);
    setScheduledPage(0);
  }, [category, query, setOngoingPage, setScheduledPage]);

  // 아래쪽 "진행 중인 공동구매" 섹션 전용 무한 스크롤 - sentinel이 뷰포트에 들어오면 다음
  // 커서를 불러온다. 위쪽 "상품" 섹션은 버튼 방식이라 이 관찰 대상이 아니다.
  const groupBuySentinelRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!isSearchMode) return;
    const el = groupBuySentinelRef.current;
    if (!el) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting && groupBuyHasNext && !groupBuyLoadingMore) {
          void loadMoreGroupBuyResults();
        }
      },
      { rootMargin: "200px" },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [isSearchMode, groupBuyHasNext, groupBuyLoadingMore, loadMoreGroupBuyResults]);

  const baseDeals = scheduledView ? scheduled : ongoing;

  const filtered = useMemo(() => {
    return baseDeals.filter((item) => {
      const matchesCategory = category === "전체" || item.category === category;
      const matchesQuery =
        query.trim().length === 0 ||
        item.title.toLowerCase().includes(query.toLowerCase()) ||
        item.producerName.toLowerCase().includes(query.toLowerCase());
      return matchesCategory && matchesQuery;
    });
  }, [baseDeals, category, query]);

  function resetFilters() {
    setQuery("");
    setCategory("전체");
    setSort("popular");
    setSearchSort("RELEVANCE");
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-6 py-9">
      <div className="space-y-4">
        <div className="flex items-center justify-between gap-3">
          {isSearchMode ? (
            <div />
          ) : (
            <DealsSubNav categories={CATALOG_CATEGORIES} categoryValue={category} onCategoryChange={setCategory} />
          )}
          {isSearchMode ? (
            <select
              value={searchSort}
              onChange={(e) => setSearchSort(e.target.value as SearchSortType)}
              className="h-10 shrink-0 rounded-xl border border-wb-line bg-wb-surface px-3 text-sm font-semibold"
            >
              {Object.entries(SEARCH_SORT_LABEL).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          ) : (
            <select
              value={sort}
              onChange={(e) => setSort(e.target.value as Sort)}
              className="h-10 shrink-0 rounded-xl border border-wb-line bg-wb-surface px-3 text-sm font-semibold"
            >
              {Object.entries(SORT_LABEL).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          )}
        </div>
      </div>

      <div>
        <h1 className="text-3xl font-bold">
          {isSearchMode ? `'${query}' 검색 결과` : scheduledView ? "진행 예정 공동구매" : "공동구매 둘러보기"}
        </h1>
        <p className="mt-1 text-sm text-wb-secondary">
          {isSearchMode
            ? "검색어와 관련된 상품과 진행 중인 공동구매를 보여드려요."
            : scheduledView
              ? "곧 시작하는 공동구매를 미리 만나보세요."
              : "카테고리와 가격을 비교해 나에게 맞는 상품을 찾아보세요."}
        </p>
      </div>

      {!isSearchMode && (
        <div className="flex items-center justify-between">
          <p className="text-base font-bold">검색 결과 {filtered.length}개</p>
        </div>
      )}

      {isSearchMode ? (
        <div className="space-y-10">
          <section className="space-y-4">
            <h2 className="text-xl font-bold">상품</h2>
            {productsLoading ? (
              <p className="py-10 text-center text-sm text-wb-secondary">검색하는 중...</p>
            ) : productsError ? (
              <p className="py-10 text-center text-sm text-wb-secondary">{productsError}</p>
            ) : productResults.length === 0 ? (
              <div className="flex flex-col items-center gap-2 py-10 text-center">
                <Search className="h-8 w-8 text-wb-green" strokeWidth={1.5} />
                <p className="text-sm text-wb-secondary">검색된 상품이 없어요.</p>
              </div>
            ) : (
              <>
                <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
                  {productResults.map((item) => (
                    <ProductSearchCard key={item.id} item={item} variant="product" />
                  ))}
                </div>
                {productsHasNext && (
                  <div className="flex justify-center">
                    <Button variant="secondary" loading={productsLoadingMore} onClick={() => void loadMoreProducts()}>
                      더 보기
                    </Button>
                  </div>
                )}
              </>
            )}
          </section>

          <section className="space-y-4">
            <h2 className="text-xl font-bold text-wb-green">진행 중인 공동구매</h2>
            {groupBuyLoading ? (
              <p className="py-10 text-center text-sm text-wb-secondary">검색하는 중...</p>
            ) : groupBuyError ? (
              <p className="py-10 text-center text-sm text-wb-secondary">{groupBuyError}</p>
            ) : groupBuyResults.length === 0 ? (
              <div className="flex flex-col items-center gap-2 py-10 text-center">
                <Search className="h-8 w-8 text-wb-green" strokeWidth={1.5} />
                <p className="text-sm text-wb-secondary">지금 진행 중인 공동구매 중엔 없어요.</p>
              </div>
            ) : (
              <>
                <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
                  {groupBuyResults.map((item) => (
                    <ProductSearchCard key={item.id} item={item} variant="groupBuy" />
                  ))}
                </div>
                <div ref={groupBuySentinelRef} className="h-1" />
                {groupBuyLoadingMore && <p className="py-4 text-center text-sm text-wb-secondary">불러오는 중...</p>}
              </>
            )}
          </section>
        </div>
      ) : isProductsView ? (
        <div className="space-y-4">
          <div className="flex items-center justify-between gap-3">
            <div className="flex gap-2">
              <select
                value={productParentCategoryId ?? ""}
                onChange={(e) => {
                  const id = e.target.value ? Number(e.target.value) : null;
                  setProductParentCategoryId(id);
                  setProductSubCategoryId(null);
                }}
                className="h-10 shrink-0 rounded-xl border border-wb-line bg-wb-surface px-3 text-sm font-semibold"
              >
                <option value="">전체 카테고리</option>
                {productCategories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.categoryName}
                  </option>
                ))}
              </select>
              {productSubCategories.length > 0 && (
                <select
                  value={productSubCategoryId ?? ""}
                  onChange={(e) => setProductSubCategoryId(e.target.value ? Number(e.target.value) : null)}
                  className="h-10 shrink-0 rounded-xl border border-wb-line bg-wb-surface px-3 text-sm font-semibold"
                >
                  <option value="">전체</option>
                  {productSubCategories.map((sub) => (
                    <option key={sub.id} value={sub.id}>
                      {sub.categoryName}
                    </option>
                  ))}
                </select>
              )}
            </div>
            <select
              value={productSort}
              onChange={(e) => setProductSort(e.target.value as ProductSortType)}
              className="h-10 shrink-0 rounded-xl border border-wb-line bg-wb-surface px-3 text-sm font-semibold"
            >
              {Object.entries(PRODUCT_SORT_LABEL).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          {productListLoading ? (
            <p className="py-20 text-center text-sm text-wb-secondary">불러오는 중...</p>
          ) : productListError ? (
            <p className="py-20 text-center text-sm text-wb-secondary">{productListError}</p>
          ) : productListItems.length === 0 ? (
            <div className="flex flex-col items-center gap-3 py-20 text-center">
              <Search className="h-9 w-9 text-wb-green" strokeWidth={1.5} />
              <p className="text-lg font-bold">등록된 상품이 없어요</p>
            </div>
          ) : (
            <>
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
                {productListItems.map((item) => (
                  <ProductCard key={item.id} item={item} />
                ))}
              </div>
              <div ref={productListSentinelRef} className="h-1" />
              {productListLoadingMore && (
                <p className="py-4 text-center text-sm text-wb-secondary">불러오는 중...</p>
              )}
            </>
          )}
        </div>
      ) : loading ? (
        <p className="py-20 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : filtered.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-20 text-center">
          <Search className="h-9 w-9 text-wb-green" strokeWidth={1.5} />
          <p className="text-lg font-bold">조건에 맞는 공동구매가 없어요</p>
          <p className="text-sm text-wb-secondary">검색어나 카테고리를 바꿔보세요.</p>
          <Button variant="secondary" onClick={resetFilters}>
            필터 초기화
          </Button>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {filtered.map((item) => (
              <GroupBuyCard key={item.id} item={item} />
            ))}
          </div>
          {totalPages > 1 && (
            <div className="flex justify-center gap-2">
              <Button
                variant="secondary"
                className="px-3 py-1.5 text-xs"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                이전
              </Button>
              <span className="flex items-center px-2 text-xs text-wb-secondary">
                {page + 1} / {totalPages}
              </span>
              <Button
                variant="secondary"
                className="px-3 py-1.5 text-xs"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                다음
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
