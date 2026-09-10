import type { CategoryTreeResponse } from "@/lib/api/types";

export const MOCK_CATEGORY_TREE: CategoryTreeResponse[] = [
  {
    id: 1,
    categoryName: "식품",
    sortOrder: 1,
    children: [
      { id: 11, categoryName: "과일", sortOrder: 1, children: [] },
      { id: 12, categoryName: "채소", sortOrder: 2, children: [] },
      { id: 13, categoryName: "수산물", sortOrder: 3, children: [] },
    ],
  },
  {
    id: 2,
    categoryName: "생활용품",
    sortOrder: 2,
    children: [
      { id: 21, categoryName: "세제", sortOrder: 1, children: [] },
      { id: 22, categoryName: "휴지", sortOrder: 2, children: [] },
      { id: 23, categoryName: "청소용품", sortOrder: 3, children: [] },
    ],
  },
  {
    id: 3,
    categoryName: "뷰티",
    sortOrder: 3,
    children: [
      { id: 31, categoryName: "스킨케어", sortOrder: 1, children: [] },
      { id: 32, categoryName: "바디케어", sortOrder: 2, children: [] },
    ],
  },
];
