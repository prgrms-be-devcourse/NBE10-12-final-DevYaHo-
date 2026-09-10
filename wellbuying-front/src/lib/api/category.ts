import { http, ApiError } from "@/lib/api/http";
import { MOCK_CATEGORY_TREE } from "@/lib/mock/categories";
import type { CategoryCreateRequest, CategoryResponse, CategoryTreeResponse, CategoryUpdateRequest } from "@/lib/api/types";

// 개발 환경에서 백엔드가 안 떠 있어도 상품 등록 모달을 확인할 수 있도록,
// 카테고리 조회가 네트워크/서버 오류로 실패하면 목데이터로 대체한다.
// 운영 환경(NODE_ENV === "production")에서는 대체 없이 그대로 예외를 던진다.
export async function listCategories(): Promise<CategoryTreeResponse[]> {
  try {
    return await http.get<CategoryTreeResponse[]>("/api/categories");
  } catch (error) {
    if (process.env.NODE_ENV !== "production") {
      const reason = error instanceof ApiError ? `${error.status} ${error.code}` : "네트워크 오류";
      console.warn(`[category] /api/categories 실패(${reason}) — 목데이터로 대체합니다.`);
      return MOCK_CATEGORY_TREE;
    }
    throw error;
  }
}

// ── 관리자 전용 CRUD (ADMIN 권한 필요) ──────────────────────────────────────

// parentId가 null이면 최상위(1뎁스), 있으면 해당 부모의 하위(2뎁스)로 생성
export function adminCreateCategory(body: CategoryCreateRequest): Promise<CategoryResponse> {
  return http.post<CategoryResponse>("/api/admin/categories", body, { auth: true });
}

// 카테고리명 변경 (2뎁스 구조에서 parentId 변경은 미지원)
export function adminUpdateCategory(id: number, body: CategoryUpdateRequest): Promise<CategoryResponse> {
  return http.patch<CategoryResponse>(`/api/admin/categories/${id}`, body, { auth: true });
}

// 자식 카테고리 또는 참조 상품이 있으면 백엔드에서 400으로 차단됨
export function adminDeleteCategory(id: number): Promise<void> {
  return http.delete<void>(`/api/admin/categories/${id}`, { auth: true });
}

export async function adminReorderCategories(
  requests: { id: number; sortOrder: number }[]
): Promise<void> {
  return http.put("/api/admin/categories/reorder", requests, { auth: true });
}
