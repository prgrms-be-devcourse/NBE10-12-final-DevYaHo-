import type { ColorToken } from "@/lib/mock/types";

// 상품명/카테고리는 이제 백엔드 Product 도메인의 실제 값을 쓴다(GroupBuySummaryResponse/DetailResponse에 포함).
// 이 파일은 그 외의 순수 장식용 필드(아이콘/색/요약·상세 문구)만 담당하고, GroupBuySeedRunner(백엔드)가
// 등록하는 8개 시드 상품의 정확한 상품명을 키로 매칭한다 - productId는 재기동마다 새로 발급돼(고정 리터럴 아님) 키로 쓸 수 없다.
export type GroupBuyCatalogEntry = {
  producerName: string;
  icon: string;
  tint: ColorToken;
  summary: string;
  detail: string;
};

const CATALOG: Record<string, GroupBuyCatalogEntry> = {
  "유기농 주방 세제": {
    producerName: "푸른살림 연구소",
    icon: "droplet",
    tint: "herb",
    summary: "식물 유래 성분 98%, 필요한 만큼만 만들어 낭비를 줄여요.",
    detail: "자극적인 향과 불필요한 포장을 덜어낸 주방 세제입니다.",
  },
  "천도복숭아": {
    producerName: "김해 과수원 박정우",
    icon: "leaf",
    tint: "citrus",
    summary: "수확일에 맞춰 보내는 새콤달콤한 제철 복숭아 2kg.",
    detail: "주문이 모인 만큼 수확해 선별하고 바로 발송합니다.",
  },
  "코튼 베이직 티셔츠": {
    producerName: "스튜디오 모노",
    icon: "shirt",
    tint: "ocean",
    summary: "원단과 봉제 공정을 모두 공개한 30수 코튼 티셔츠.",
    detail: "유행을 타지 않는 실루엣과 촘촘한 봉제로 오래 입을 수 있게 만들었습니다.",
  },
  "에티오피아 스페셜티 원두": {
    producerName: "로스터리 시옷",
    icon: "coffee",
    tint: "sand",
    summary: "매주 소량만 로스팅해서 신선함을 지킨 원두예요.",
    detail: "주문이 들어온 만큼만 로스팅해 유통 기간을 최소화합니다.",
  },
  "지리산 야생화 벌꿀": {
    producerName: "지리산 벌마을",
    icon: "leaf",
    tint: "citrus",
    summary: "한 계절 동안 모은 야생화 꿀을 병입 그대로 보내드려요.",
    detail: "설탕이나 시럽을 섞지 않은 자연 그대로의 벌꿀입니다.",
  },
  "고체 비누 세트": {
    producerName: "비누공방 결",
    icon: "sparkles",
    tint: "herb",
    summary: "동물성 원료 없이 만든 저자극 고체 비누 5종이에요.",
    detail: "콜드 프로세스 방식으로 오랜 시간 숙성시켜 세정력과 순함을 함께 잡았습니다.",
  },
  "소이 캔들": {
    producerName: "스튜디오 온기",
    icon: "sparkles",
    tint: "berry",
    summary: "콩기름으로 만들어 그을음이 적고 오래 타는 캔들이에요.",
    detail: "천연 왁스와 면 심지를 사용해 유해 그을음을 줄였습니다.",
  },
  "스테인리스 텀블러": {
    producerName: "리빙랩 오늘",
    icon: "package",
    tint: "sky",
    summary: "이중 진공 구조라 아침에 담은 온도가 저녁까지 유지돼요.",
    detail: "식품용 스테인리스 원판을 사용하고, 뚜껑 실링을 이중으로 처리했습니다.",
  },
  "제주 유기농 감귤": {
    producerName: "제주 감귤말농장",
    icon: "leaf",
    tint: "citrus",
    summary: "당도가 오른 만큼만 수확해 보내는 제주 유기농 감귤이에요.",
    detail: "화학 비료 없이 키운 감귤을 주문 들어온 물량만큼 그날 선별합니다.",
  },
  "리넨 반팔 셔츠": {
    producerName: "스튜디오 리넨웍스",
    icon: "shirt",
    tint: "sky",
    summary: "구김이 적고 통기성이 좋은 여름용 리넨 반팔 셔츠예요.",
    detail: "린넨 100% 원단을 워싱 가공해 첫 세탁부터 편하게 입을 수 있습니다.",
  },
  "핸드메이드 도자기 머그": {
    producerName: "아틀리에 흙",
    icon: "palette",
    tint: "sand",
    summary: "하나하나 물레로 빚어 미세하게 다른 표정을 가진 머그예요.",
    detail: "식품안전 유약을 사용했고, 전자레인지와 식기세척기에도 사용할 수 있습니다.",
  },
  "저온숙성 원목 도마": {
    producerName: "목공방 결",
    icon: "package",
    tint: "herb",
    summary: "칼자국이 남아도 위생적인 저온숙성 원목 도마예요.",
    detail: "원목을 저온에서 오래 건조시켜 뒤틀림과 갈라짐을 줄였습니다.",
  },
  "무항생제 유정란": {
    producerName: "청정목장 이현수",
    icon: "gift",
    tint: "berry",
    summary: "산란 당일 세척·포장해 바로 보내는 무항생제 유정란이에요.",
    detail: "항생제 없이 방사 사육한 닭이 낳은 달걀만 선별해 보냅니다.",
  },
  "삼베 매트리스 커버": {
    producerName: "직물공방 결이",
    icon: "droplet",
    tint: "ocean",
    summary: "통풍이 잘 되어 여름철 쾌적하게 쓸 수 있는 삼베 커버예요.",
    detail: "국내산 삼베 원단을 사용해 습기를 빠르게 흡수하고 발산합니다.",
  },
  "울 혼방 니트 가디건": {
    producerName: "니트하우스 온",
    icon: "sparkles",
    tint: "citrus",
    summary: "가볍게 걸치기 좋은 울 혼방 니트 가디건이에요.",
    detail: "보풀이 적은 혼방사를 사용해 오래 입어도 태가 유지됩니다.",
  },
  "천연 왁스 캔들 세트": {
    producerName: "왁스랩 소소",
    icon: "coffee",
    tint: "sand",
    summary: "은은한 조도로 공간 분위기를 바꿔주는 천연 왁스 캔들이에요.",
    detail: "콩기름과 밀랍을 섞은 천연 왁스로 만들어 그을음이 적습니다.",
  },
};

const DEFAULT_ENTRY: GroupBuyCatalogEntry = {
  producerName: "생산자",
  icon: "package",
  tint: "sky",
  summary: "",
  detail: "",
};

export function resolveCatalogEntry(productName: string): GroupBuyCatalogEntry {
  return CATALOG[productName] ?? DEFAULT_ENTRY;
}

export const CATALOG_CATEGORIES = ["전체", "식품", "생활", "패션", "기타"];
