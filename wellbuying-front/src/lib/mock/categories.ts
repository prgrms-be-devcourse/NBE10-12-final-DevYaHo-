import type { CategoryTreeResponse } from "@/lib/api/types";

// 백엔드 없이 로컬에서 상품 등록 모달을 확인할 때 쓰는 카테고리 목데이터.
// 백엔드 시드(GroupBuySeedRunner)는 현재 최상위 카테고리(식품/생활/패션)만 만든다.
// 나중에 2단계 연동 드롭다운으로 확장할 때 미리 확인할 수 있도록 children도 채워 둔다.
// 실제 API 응답과 동일하게 CategoryTreeResponse[] 형태를 유지한다.
export const MOCK_CATEGORY_TREE: CategoryTreeResponse[] = [
  {
    id: 1,
    categoryName: "식품",
    children: [
      { id: 11, categoryName: "과일", children: [] },
      { id: 12, categoryName: "커피/원두", children: [] },
      { id: 13, categoryName: "가공식품", children: [] },
    ],
  },
  {
    id: 2,
    categoryName: "생활",
    children: [
      { id: 21, categoryName: "주방", children: [] },
      { id: 22, categoryName: "욕실", children: [] },
      { id: 23, categoryName: "홈데코", children: [] },
    ],
  },
  {
    id: 3,
    categoryName: "패션",
    children: [
      { id: 31, categoryName: "의류", children: [] },
      { id: 32, categoryName: "잡화", children: [] },
    ],
  },
];
