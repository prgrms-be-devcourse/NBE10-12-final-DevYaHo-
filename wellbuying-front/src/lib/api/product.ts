import { http } from "@/lib/api/http";
import type {
  CursorPageResponse,
  ProductAutocompleteResponse,
  ProductCreateRequest,
  ProductDeleteRequest,
  ProductDetailResponse,
  ProductMineResponse,
  ProductSearchResponse,
  ProductSortType,
  ProductSummaryResponse,
  ProductUpdateRequest,
  SearchSortType,
  SliceResponse,
} from "@/lib/api/types";

export function createProduct(request: ProductCreateRequest): Promise<void> {
  return http.post<void>("/api/products", request, { auth: true });
}

export function listMyProducts(): Promise<SliceResponse<ProductMineResponse>> {
  return http.get<SliceResponse<ProductMineResponse>>("/api/products/mine", { auth: true });
}

export function getProduct(productId: number): Promise<ProductDetailResponse> {
  return http.get<ProductDetailResponse>(`/api/products/${productId}`);
}

export function getProducts(params: {
  category?: number;
  minPrice?: number;
  maxPrice?: number;
  sort?: ProductSortType;
  cursor?: string;
  size?: number;
}): Promise<CursorPageResponse<ProductSummaryResponse>> {
  const query = new URLSearchParams();
  if (params.category != null) query.set("category", String(params.category));
  if (params.minPrice != null) query.set("minPrice", String(params.minPrice));
  if (params.maxPrice != null) query.set("maxPrice", String(params.maxPrice));
  if (params.sort) query.set("sort", params.sort);
  if (params.cursor) query.set("cursor", params.cursor);
  query.set("size", String(params.size ?? 20));
  return http.get<CursorPageResponse<ProductSummaryResponse>>(`/api/products?${query.toString()}`);
}

export function searchProducts(params: {
  keyword: string;
  sort?: SearchSortType;
  cursor?: string;
  size?: number;
  activeGroupBuyOnly?: boolean;
}): Promise<CursorPageResponse<ProductSearchResponse>> {
  const query = new URLSearchParams();
  query.set("keyword", params.keyword);
  query.set("activeGroupBuyOnly", String(params.activeGroupBuyOnly ?? true));
  if (params.sort) query.set("sort", params.sort);
  if (params.cursor) query.set("cursor", params.cursor);
  query.set("size", String(params.size ?? 12));
  return http.get<CursorPageResponse<ProductSearchResponse>>(`/api/products/search?${query.toString()}`);
}

export function getPopularProducts(): Promise<ProductSummaryResponse[]> {
  return http.get<ProductSummaryResponse[]>("/api/products/popular");
}

export function updateProduct(productId: number, request: ProductUpdateRequest): Promise<void> {
  return http.patch<void>(`/api/products/${productId}`, request, { auth: true });
}

export function deleteProduct(productId: number, request: ProductDeleteRequest): Promise<void> {
  return http.post<void>(`/api/products/${productId}/delete`, request, { auth: true });
}

export function autocompleteProducts(keyword: string): Promise<ProductAutocompleteResponse[]> {
  return http.get<ProductAutocompleteResponse[]>(
    `/api/products/search/autocomplete?keyword=${encodeURIComponent(keyword)}`,
  );
}

