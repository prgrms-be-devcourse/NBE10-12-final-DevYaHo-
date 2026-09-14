"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Search } from "lucide-react";
import { GroupBuyCard } from "@/components/deal/GroupBuyCard";
import { ProductCard } from "@/components/deal/ProductCard";
import { ProductSearchCard } from "@/components/deal/ProductSearchCard";
import { Button } from "@/components/ui/Button";
import { SelectField } from "@/components/ui/SelectField";
import { listCategories } from "@/lib/api/category";
import { useGroupBuyList } from "@/lib/groupBuy/useGroupBuyList";
import { useProductList } from "@/lib/product/useProductList";
import { useProductSearch } from "@/lib/product/useProductSearch";
import type { CategoryTreeResponse, ProductSortType } from "@/lib/api/types";

type Sort = "popular" | "new" | "closing";
type StatusFilter = "all" | "ongoing" | "scheduled";

function toStatusFilter(value: string | null): StatusFilter {
  if (value === "scheduled") return "scheduled";
  if (value === "ongoing") return "ongoing";
  return "all";
}

const STATUS_FILTER_OPTIONS = [
  { value: "all", label: "전체" },
  { value: "ongoing", label: "진행중" },
  { value: "scheduled", label: "진행예정" },
  { value: "closing", label: "마감임박" },
];

const SORT_PARAM: Record<Sort, string> = {
  popular: "viewCount,desc",
  new: "createdAt,desc",
  closing: "endAt,asc",
};

const PRODUCT_SORT_LABEL: Record<ProductSortType, string> = {
  LATEST: "최신순",
  POPULAR: "인기순",
  PRICE_ASC: "가격 낮은순",
  PRICE_DESC: "가격 높은순",
};

const PAGE_SIZE = 20;

function isSort(value: string | null): value is Sort {
  return !!value && value in SORT_PARAM;
}

export default function ExplorePage() {
  const searchParams = useSearchParams();
  const isProductsView = searchParams.get("view") === "products";

  const [query, setQuery] = useState(() => searchParams.get("q") ?? "");
  const [category, setCategory] = useState(() => searchParams.get("category") ?? "전체");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>(() => toStatusFilter(searchParams.get("status")));
  const [sort, setSort] = useState<Sort>(() => {
    const param = searchParams.get("sort");
    return isSort(param) ? param : "popular";
  });
  const [productSort, setProductSort] = useState<ProductSortType>("LATEST");
  const [productParentCategoryId, setProductParentCategoryId] = useState<number | null>(null);
  const [productSubCategoryId, setProductSubCategoryId] = useState<number | null>(null);
  const [browseSubCategoryId, setBrowseSubCategoryId] = useState<number | null>(null);
  const [productCategories, setProductCategories] = useState<CategoryTreeResponse[]>([]);
  const [categoriesLoaded, setCategoriesLoaded] = useState(false);

  const productCategoryId = productSubCategoryId ?? productParentCategoryId;
  const selectedProductParent = productCategories.find((c) => c.id === productParentCategoryId) ?? null;
  const productSubCategories = selectedProductParent?.children ?? [];

  // 헤더 카테고리 탭(CategoryHoverTab)이 넘기는 category는 최상위 카테고리 "이름"이라, 서버에 넘길
  // categoryId로 바꾸려면 최상위 카테고리 목록이 필요하다 - 상품 뷰 여부와 무관하게 항상 받아온다
  const categoryId = category === "전체" ? undefined : productCategories.find((c) => c.categoryName === category)?.id;
  // "전체"가 아닌데 categoryId를 아직 못 찾은 건, 목록이 안 실려서인지("아직 로딩 중" - 대기해야 함)
  // 정말 없는 카테고리라서인지("로딩 끝났는데도 매칭 실패" - 무필터로 보여줘도 됨) 구분해야 한다.
  // categoriesLoaded로 그 둘을 나눠, 로딩 중엔 categoryId=undefined인 채로 목록을 조회하지 않게 막는다
  const categoryReady = category === "전체" || categoriesLoaded;
  const browseSubCategories = productCategories.find((c) => c.categoryName === category)?.children ?? [];
  const effectiveCategoryId = browseSubCategoryId ?? categoryId;
  const selectedSubCategoryName = browseSubCategories.find((c) => c.id === browseSubCategoryId)?.categoryName;
  const categoryLabel = selectedSubCategoryName ?? (category !== "전체" ? category : null);
  const statusLabel =
    sort === "closing"
      ? "마감임박 공동구매"
      : statusFilter === "scheduled"
        ? "진행 예정 공동구매"
        : statusFilter === "ongoing"
          ? "진행중인 공동구매"
          : null;

  useEffect(() => {
    setBrowseSubCategoryId(null);
  }, [category]);

  useEffect(() => {
    let ignore = false;
    listCategories()
      .then((tree) => {
        if (!ignore) setProductCategories(tree);
      })
      .catch(() => {
        if (!ignore) setProductCategories([]);
      })
      .finally(() => {
        if (!ignore) setCategoriesLoaded(true);
      });
    return () => {
      ignore = true;
    };
  }, []);

  const {
    items: productListItems,
    loading: productListLoading,
    loadingMore: productListLoadingMore,
    hasNext: productListHasNext,
    error: productListError,
    loadMore: loadMoreProductList,
  } = useProductList({ category: productCategoryId, sort: productSort, size: 20, enabled: isProductsView });

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
    loadingMore: ongoingLoadingMore,
    hasNext: ongoingHasNext,
    totalElements: ongoingTotalElements,
    loadMore: loadMoreOngoing,
  } = useGroupBuyList("ONGOING", {
    sort: sortParam,
    size: PAGE_SIZE,
    categoryId: effectiveCategoryId,
    enabled: categoryReady,
  });
  const {
    items: scheduled,
    loading: scheduledLoading,
    loadingMore: scheduledLoadingMore,
    hasNext: scheduledHasNext,
    totalElements: scheduledTotalElements,
    loadMore: loadMoreScheduled,
  } = useGroupBuyList("READY", {
    sort: sortParam,
    size: PAGE_SIZE,
    categoryId: effectiveCategoryId,
    enabled: categoryReady,
  });

  const {
    items: groupBuyResults,
    loading: groupBuyLoading,
    loadingMore: groupBuyLoadingMore,
    hasNext: groupBuyHasNext,
    error: groupBuyError,
    loadMore: loadMoreGroupBuyResults,
  } = useProductSearch(query, { size: PAGE_SIZE, activeGroupBuyOnly: true });

  // 마감임박(sort=closing)은 이미 시작한 공동구매 중 곧 끝나는 것만 의미가 있으므로,
  // status 파라미터와 무관하게 항상 진행중 목록만 대상으로 한다
  const effectiveStatus: StatusFilter = sort === "closing" ? "ongoing" : statusFilter;

  const loading =
    effectiveStatus === "all" ? ongoingLoading || scheduledLoading : effectiveStatus === "scheduled" ? scheduledLoading : ongoingLoading;
  const loadingMore =
    effectiveStatus === "all"
      ? ongoingLoadingMore || scheduledLoadingMore
      : effectiveStatus === "scheduled"
        ? scheduledLoadingMore
        : ongoingLoadingMore;
  const hasNext =
    effectiveStatus === "all" ? ongoingHasNext || scheduledHasNext : effectiveStatus === "scheduled" ? scheduledHasNext : ongoingHasNext;
  const totalCount =
    effectiveStatus === "all"
      ? ongoingTotalElements + scheduledTotalElements
      : effectiveStatus === "scheduled"
        ? scheduledTotalElements
        : ongoingTotalElements;
  // "전체"는 진행중/진행예정 두 목록을 합쳐 보여주므로, 각자 더 가져올 페이지가 남아있는 목록만 이어서 불러온다
  const loadMore = useCallback(() => {
    if (effectiveStatus === "all") {
      if (ongoingHasNext) loadMoreOngoing();
      if (scheduledHasNext) loadMoreScheduled();
    } else if (effectiveStatus === "scheduled") {
      loadMoreScheduled();
    } else {
      loadMoreOngoing();
    }
  }, [effectiveStatus, ongoingHasNext, scheduledHasNext, loadMoreOngoing, loadMoreScheduled]);

  useEffect(() => {
    setQuery(searchParams.get("q") ?? "");
    setCategory(searchParams.get("category") ?? "전체");
    setStatusFilter(toStatusFilter(searchParams.get("status")));
    const param = searchParams.get("sort");
    setSort(isSort(param) ? param : "popular");
  }, [searchParams]);

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

  // 검색 모드가 아닌 일반 둘러보기 목록(진행중/진행예정/전체) 전용 무한 스크롤
  const dealsSentinelRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (isSearchMode || isProductsView) return;
    const el = dealsSentinelRef.current;
    if (!el) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting && hasNext && !loadingMore) {
          loadMore();
        }
      },
      { rootMargin: "200px" },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [isSearchMode, isProductsView, hasNext, loadingMore, loadMore]);

  const baseDeals = useMemo(() => {
    if (effectiveStatus === "all") return [...ongoing, ...scheduled];
    return effectiveStatus === "scheduled" ? scheduled : ongoing;
  }, [effectiveStatus, ongoing, scheduled]);

  // 카테고리는 useGroupBuyList에 categoryId로 넘겨 서버에서 필터링한다 - 여기서는 검색어만 클라이언트에서 한 번 더 좁힌다
  const filtered = useMemo(() => {
    return baseDeals.filter((item) => {
      const matchesQuery =
        query.trim().length === 0 ||
        item.title.toLowerCase().includes(query.toLowerCase()) ||
        item.producerName.toLowerCase().includes(query.toLowerCase());
      return matchesQuery;
    });
  }, [baseDeals, query]);

  function resetFilters() {
    setQuery("");
    setCategory("전체");
    setSort("popular");
    setBrowseSubCategoryId(null);
    setStatusFilter("all");
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-6 py-9">
      <div>
        <h1 className="text-3xl font-bold">{isSearchMode ? `'${query}' 검색 결과` : (categoryLabel ?? statusLabel ?? "공동구매 둘러보기")}</h1>
        <p className="mt-1 text-sm text-wb-secondary">
          {isSearchMode
            ? "검색어와 관련된 진행 중인 공동구매를 보여드려요."
            : sort === "closing"
              ? "마감이 얼마 남지 않은 공동구매를 확인해보세요."
              : statusFilter === "scheduled"
                ? "곧 시작하는 공동구매를 미리 만나보세요."
                : statusFilter === "ongoing"
                  ? "지금 참여할 수 있는 공동구매를 만나보세요."
                  : "카테고리와 가격을 비교해 나에게 맞는 상품을 찾아보세요."}
        </p>
      </div>

      {!isSearchMode && !isProductsView && browseSubCategories.length > 0 && (
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setBrowseSubCategoryId(null)}
            className={`rounded-full border px-4 py-1.5 text-sm font-semibold transition ${
              browseSubCategoryId === null
                ? "border-wb-green bg-wb-green text-white"
                : "border-wb-line bg-wb-surface text-wb-secondary hover:border-wb-green"
            }`}
          >
            전체
          </button>
          {browseSubCategories.map((sub) => (
            <button
              key={sub.id}
              type="button"
              onClick={() => setBrowseSubCategoryId(sub.id)}
              className={`rounded-full border px-4 py-1.5 text-sm font-semibold transition ${
                browseSubCategoryId === sub.id
                  ? "border-wb-green bg-wb-green text-white"
                  : "border-wb-line bg-wb-surface text-wb-secondary hover:border-wb-green"
              }`}
            >
              {sub.categoryName}
            </button>
          ))}
        </div>
      )}

      {!isSearchMode && !isProductsView && category === "전체" && (
        <p className="text-base font-bold">{totalCount}개의 공동구매가 있어요</p>
      )}

      {/* 헤더 탭은 카테고리 구분 없이 상태만 훑어보는 용도이고, 이 셀렉트박스는 카테고리를
          선택한 뒤 그 안에서 상태를 좁혀보기 위한 용도라 카테고리 선택 시에만 노출한다 */}
      {!isSearchMode && !isProductsView && category !== "전체" && (
        <div className="flex items-center justify-between">
          <p className="text-base font-bold">{totalCount}개의 공동구매가 있어요</p>
          <SelectField
            value={statusFilter === "all" ? "all" : statusFilter === "scheduled" ? "scheduled" : sort === "closing" ? "closing" : "ongoing"}
            onChange={(value) => {
              setStatusFilter(value === "all" ? "all" : value === "scheduled" ? "scheduled" : "ongoing");
              setSort(value === "closing" ? "closing" : "popular");
            }}
            options={STATUS_FILTER_OPTIONS}
            className="w-32 shrink-0"
          />
        </div>
      )}

      {isSearchMode ? (
        groupBuyLoading ? (
          <p className="py-20 text-center text-sm text-wb-secondary">검색하는 중...</p>
        ) : groupBuyError ? (
          <p className="py-20 text-center text-sm text-wb-secondary">{groupBuyError}</p>
        ) : groupBuyResults.length === 0 ? (
          <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-wb-line py-16 text-center">
            <Search className="h-8 w-8 text-wb-secondary" strokeWidth={1.5} />
            <p className="text-sm font-semibold text-wb-secondary">지금 진행 중인 공동구매 중엔 없어요.</p>
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
        )
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
          <div ref={dealsSentinelRef} className="h-1" />
          {loadingMore && <p className="py-4 text-center text-sm text-wb-secondary">불러오는 중...</p>}
        </>
      )}
    </div>
  );
}
