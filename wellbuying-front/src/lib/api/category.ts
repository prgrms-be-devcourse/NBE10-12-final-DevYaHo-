import { http } from "@/lib/api/http";
import type { CategoryTreeResponse } from "@/lib/api/types";

export function listCategories(): Promise<CategoryTreeResponse[]> {
  return http.get<CategoryTreeResponse[]>("/api/categories");
}
