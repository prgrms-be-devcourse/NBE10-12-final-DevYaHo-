import { http } from "@/lib/api/http";
import type {
  ProductCreateRequest,
  ProductDetailResponse,
  ProductMineResponse,
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
