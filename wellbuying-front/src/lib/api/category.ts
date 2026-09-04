import { http, ApiError } from "@/lib/api/http";
import { MOCK_CATEGORY_TREE } from "@/lib/mock/categories";
import type { CategoryTreeResponse } from "@/lib/api/types";

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
