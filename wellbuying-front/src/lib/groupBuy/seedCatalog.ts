import type { ColorToken } from "@/lib/mock/types";

// 상품명/카테고리는 이제 백엔드 Product 도메인의 실제 값을 쓴다(GroupBuySummaryResponse/DetailResponse에 포함).
// 이 파일은 그 외의 순수 장식용 필드(아이콘/색/요약·상세 문구)만 담당하고, GroupBuySeedRunner(백엔드)가
// 등록하는 8개 시드 상품의 정확한 상품명을 키로 매칭한다 - productId는 재기동마다 새로 발급돼(고정 리터럴 아님) 키로 쓸 수 없다.
export type StoryBlock = {
  headline: string;
  body: string;
};

export type GroupBuyCatalogEntry = {
  producerName: string;
  icon: string;
  tint: ColorToken;
  summary: string;
  detail: string;
  storyBlocks?: StoryBlock[];
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
  "해남 꿀고구마 5kg": {
    producerName: "생산자", icon: "package", tint: "sand", summary: "", detail: "",
    storyBlocks: [
      { headline: "꿀처럼 흐르는 단맛", body: "잘랐을 때 꿀이 흐르듯 진한 단물이 해남 꿀고구마의 특징이에요." },
      { headline: "실물 그대로의 크기와 색", body: "화면 속 색감과 실제 받아보시는 고구마의 차이를 최소화해서 보여드려요." },
      { headline: "해남 황토밭에서 정성껏", body: "일교차 큰 해남 황토밭에서 자란 고구마를 수확 즉시 선별했어요." },
      { headline: "무르지 않게, 안전하게", body: "충격에 약한 고구마라 완충재를 넉넉히 넣어 포장해요." },
    ],
  },
  "나주 배 8kg (가정용)": {
    producerName: "생산자", icon: "package", tint: "sand", summary: "", detail: "",
    storyBlocks: [
      { headline: "흠집은 있어도 맛은 그대로", body: "겉모습에 흠집이 있을 뿐, 당도와 과즙은 상품과 다르지 않아요." },
      { headline: "실물 그대로의 모습", body: "못난이 배 특유의 흠집도 가감 없이 그대로 보여드려요." },
      { headline: "나주 배밭에서 바로", body: "상품성만 떨어졌을 뿐 같은 나무에서 자란 배예요." },
      { headline: "무르지 않게, 안전하게", body: "물러지기 쉬운 배라 개별 포장 후 완충재로 감싸 보내드려요." },
    ],
  },
  "제주 감귤 5kg": {
    producerName: "생산자", icon: "package", tint: "sand", summary: "", detail: "",
    storyBlocks: [
      { headline: "새콤달콤 그 자체", body: "제주 노지에서 자란 감귤 특유의 새콤달콤한 맛을 그대로 담았어요." },
      { headline: "실물 그대로의 색", body: "화면과 실제 색감 차이를 최소화해서 있는 그대로 보여드려요." },
      { headline: "제주 바람과 햇빛으로", body: "노지에서 바람과 햇빛을 그대로 맞고 자란 감귤이에요." },
      { headline: "무르지 않게, 안전하게", body: "무르기 쉬운 감귤이라 통풍이 되는 포장으로 신선하게 보내드려요." },
    ],
  },
  "청송 사과 10kg (못난이)": {
    producerName: "생산자", icon: "package", tint: "sand", summary: "", detail: "",
    storyBlocks: [
      { headline: "당도는 그대로, 모양만 다르게", body: "모양이 고르지 않을 뿐 당도는 상품과 차이가 없어요." },
      { headline: "실물 그대로의 모습", body: "못난이 사과의 표면도 가감 없이 그대로 보여드려요." },
      { headline: "청송 일교차가 키운 단맛", body: "일교차 큰 청송에서 자라 단맛이 응축된 사과예요." },
      { headline: "무르지 않게, 안전하게", body: "충격에 약한 사과라 낱개 포장 후 완충재로 감싸요." },
    ],
  },
  "성주 참외 3kg": {
    producerName: "생산자", icon: "package", tint: "sand", summary: "", detail: "",
    storyBlocks: [
      { headline: "노란빛이 선명한 참외", body: "잘 익은 성주 참외 특유의 선명한 노란빛을 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "화면 속 색감과 실제 참외의 차이를 최소화해서 보여드려요." },
      { headline: "성주 여름 햇살로 키운", body: "여름 한정, 성주 특산 참외밭에서 자란 상품이에요." },
      { headline: "무르지 않게, 안전하게", body: "무르기 쉬운 참외라 완충재로 감싸 안전하게 보내드려요." },
    ],
  },
  "강원 고랭지 배추 3포기": {
    producerName: "생산자", icon: "package", tint: "herb", summary: "", detail: "",
    storyBlocks: [
      { headline: "속이 꽉 찬 배추", body: "고랭지에서 자라 속이 단단하고 꽉 찬 배추예요." },
      { headline: "실물 그대로의 모습", body: "겉잎 색과 크기까지 있는 그대로 보여드려요." },
      { headline: "고랭지 서늘한 기후로", body: "여름에도 서늘한 고랭지 기후에서 자란 배추예요." },
      { headline: "무르지 않게, 안전하게", body: "잎이 상하지 않도록 통풍 포장으로 신선하게 보내드려요." },
    ],
  },
  "무농약 방울토마토 2kg": {
    producerName: "생산자", icon: "package", tint: "herb", summary: "", detail: "",
    storyBlocks: [
      { headline: "완숙된 빨간빛", body: "나무에서 완전히 익힌 뒤 수확한 방울토마토의 빨간빛이에요." },
      { headline: "실물 그대로의 모습", body: "화면과 실제 토마토 색감 차이를 최소화해서 보여드려요." },
      { headline: "무농약으로 정성껏", body: "무농약 재배 방식으로 안심하고 드실 수 있게 키웠어요." },
      { headline: "터지지 않게, 안전하게", body: "무르기 쉬운 토마토라 완충 포장으로 안전하게 보내드려요." },
    ],
  },
  "유기농 상추 모듬 500g": {
    producerName: "생산자", icon: "package", tint: "herb", summary: "", detail: "",
    storyBlocks: [
      { headline: "당일 수확한 싱그러움", body: "쌈채소 특유의 싱그러운 초록빛을 당일 수확으로 담았어요." },
      { headline: "실물 그대로의 모습", body: "모듬 구성과 색감을 있는 그대로 보여드려요." },
      { headline: "유기농으로 정성껏", body: "유기농 인증 재배 방식으로 안심하고 드실 수 있어요." },
      { headline: "시들지 않게, 안전하게", body: "수분을 유지하는 포장으로 신선하게 배송해드려요." },
    ],
  },
  "햇양파 10kg": {
    producerName: "생산자", icon: "package", tint: "herb", summary: "", detail: "",
    storyBlocks: [
      { headline: "단단하고 실한 알", body: "저장성 좋은 햇양파 특유의 단단한 속을 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "크기와 껍질 색까지 있는 그대로 보여드려요." },
      { headline: "햇볕에 잘 말려", body: "수확 후 충분히 건조시켜 저장성을 높인 양파예요." },
      { headline: "무르지 않게, 안전하게", body: "통풍이 되는 포장으로 신선하게 보내드려요." },
    ],
  },
  "친환경 애호박 5개입": {
    producerName: "생산자", icon: "package", tint: "herb", summary: "", detail: "",
    storyBlocks: [
      { headline: "매끈한 초록빛 표면", body: "무농약 인증 애호박 특유의 매끈한 표면을 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "크기와 색감을 있는 그대로 보여드려요." },
      { headline: "무농약으로 정성껏", body: "무농약 인증을 받은 재배 방식으로 안심하고 드실 수 있어요." },
      { headline: "무르지 않게, 안전하게", body: "충격에 약한 애호박이라 완충 포장으로 보내드려요." },
    ],
  },
  "경기미 임금님표 이천쌀 10kg": {
    producerName: "생산자", icon: "package", tint: "sand", summary: "", detail: "",
    storyBlocks: [
      { headline: "갓 도정한 윤기", body: "갓 도정해서 윤기가 살아있는 이천쌀의 모습이에요." },
      { headline: "실물 그대로의 모습", body: "쌀알의 색과 윤기를 있는 그대로 보여드려요." },
      { headline: "임금님표 이천 들녘에서", body: "예로부터 밥맛 좋기로 이름난 이천 들녘에서 재배했어요." },
      { headline: "눅눅해지지 않게, 안전하게", body: "습기를 막는 포장으로 신선하게 보내드려요." },
    ],
  },
  "국산 혼합 잡곡 3kg": {
    producerName: "생산자", icon: "package", tint: "sand", summary: "", detail: "",
    storyBlocks: [
      { headline: "알알이 살아있는 9곡", body: "9가지 곡물이 고르게 섞인 모습을 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "곡물 각각의 색과 비율을 있는 그대로 보여드려요." },
      { headline: "국내산 100%로", body: "9가지 곡물 모두 국내산 100%로만 구성했어요." },
      { headline: "눅눅해지지 않게, 안전하게", body: "밀봉 포장으로 신선하게 보내드려요." },
    ],
  },
  "유기농 현미 5kg": {
    producerName: "생산자", icon: "package", tint: "sand", summary: "", detail: "",
    storyBlocks: [
      { headline: "고소한 속겨의 색", body: "속겨를 살린 유기농 현미 특유의 고소한 색을 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "현미 알의 색과 크기를 있는 그대로 보여드려요." },
      { headline: "무농약으로 정성껏", body: "무농약 인증을 받은 재배 방식으로 키운 현미예요." },
      { headline: "눅눅해지지 않게, 안전하게", body: "습기를 막는 포장으로 신선하게 보내드려요." },
    ],
  },
  "흑임자 500g": {
    producerName: "생산자", icon: "package", tint: "sand", summary: "", detail: "",
    storyBlocks: [
      { headline: "윤기 흐르는 검은 알갱이", body: "잘 볶아서 윤기가 도는 흑임자의 모습이에요." },
      { headline: "실물 그대로의 모습", body: "알갱이 크기와 색을 있는 그대로 보여드려요." },
      { headline: "고소하게 볶아서", body: "은근한 불에 정성껏 볶아 고소한 향을 살렸어요." },
      { headline: "눅눅해지지 않게, 안전하게", body: "밀봉 포장으로 고소함을 오래 유지해드려요." },
    ],
  },
  "완도 활전복 10미": {
    producerName: "생산자", icon: "package", tint: "ocean", summary: "", detail: "",
    storyBlocks: [
      { headline: "살아있는 전복의 윤기", body: "완도 청정 바다에서 자란 활전복의 윤기를 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "전복 크기와 색을 있는 그대로 보여드려요." },
      { headline: "완도 청정 바다에서", body: "맑은 완도 바닷물에서 자란 전복이에요." },
      { headline: "신선하게, 안전하게", body: "산소 포장으로 당일 활어 상태 그대로 보내드려요." },
    ],
  },
  "목포 건멸치 1kg": {
    producerName: "생산자", icon: "package", tint: "ocean", summary: "", detail: "",
    storyBlocks: [
      { headline: "은빛 그대로 마른 멸치", body: "국물용으로 좋은 대멸치의 은빛 광택을 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "멸치 크기와 건조 상태를 있는 그대로 보여드려요." },
      { headline: "목포 바다에서 잡아 바로", body: "목포 앞바다에서 잡은 멸치를 바로 건조했어요." },
      { headline: "눅눅해지지 않게, 안전하게", body: "밀봉 포장으로 눅눅해지지 않게 보내드려요." },
    ],
  },
  "신안 천일염 5kg": {
    producerName: "생산자", icon: "package", tint: "ocean", summary: "", detail: "",
    storyBlocks: [
      { headline: "3년 숙성된 하얀 결정", body: "간수를 뺀 3년 숙성 천일염의 결정을 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "결정의 크기와 색을 있는 그대로 보여드려요." },
      { headline: "신안 갯벌에서 3년", body: "신안 염전에서 만들어 3년간 간수를 뺐어요." },
      { headline: "눅눅해지지 않게, 안전하게", body: "습기를 막는 포장으로 보송하게 보내드려요." },
    ],
  },
  "제철 갈치 손질 3마리": {
    producerName: "생산자", icon: "package", tint: "ocean", summary: "", detail: "",
    storyBlocks: [
      { headline: "은빛 광택이 살아있는 갈치", body: "제철 갈치 특유의 은빛 광택을 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "손질된 상태와 크기를 있는 그대로 보여드려요." },
      { headline: "손질까지 미리 완료", body: "조리만 하면 되도록 손질을 미리 마쳤어요." },
      { headline: "신선하게, 안전하게", body: "냉동 포장으로 신선도를 유지해서 보내드려요." },
    ],
  },
  "콜롬비아 스페셜티 원두 1kg": {
    producerName: "생산자", icon: "coffee", tint: "sand", summary: "", detail: "",
    storyBlocks: [
      { headline: "갓 로스팅한 윤기", body: "중배전으로 갓 로스팅한 원두 표면의 윤기를 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "원두 색과 크기를 있는 그대로 보여드려요." },
      { headline: "주문 후 로스팅해서", body: "신선함을 위해 주문 들어온 만큼만 로스팅해요." },
      { headline: "향이 날아가지 않게", body: "밀봉 포장으로 원두 향을 그대로 지켜드려요." },
    ],
  },
  "유기농 국화차 50g": {
    producerName: "생산자", icon: "leaf", tint: "sand", summary: "", detail: "",
    storyBlocks: [
      { headline: "은은한 노란빛 꽃잎", body: "무농약 재배 국화 특유의 은은한 색을 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "말린 꽃잎의 크기와 색을 있는 그대로 보여드려요." },
      { headline: "무농약으로 정성껏", body: "무농약 인증을 받은 국화만 골라 말렸어요." },
      { headline: "향이 날아가지 않게", body: "밀봉 포장으로 은은한 향을 지켜드려요." },
    ],
  },
  "홍차 잎차 100g": {
    producerName: "생산자", icon: "leaf", tint: "sand", summary: "", detail: "",
    storyBlocks: [
      { headline: "진한 갈색의 찻잎", body: "정통 홍차 특유의 진한 갈색 찻잎을 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "찻잎의 크기와 색을 있는 그대로 보여드려요." },
      { headline: "정통 방식 그대로", body: "전통 발효 방식을 그대로 따라 만든 홍차예요." },
      { headline: "향이 날아가지 않게", body: "밀봉 포장으로 찻잎 향을 지켜드려요." },
    ],
  },
  "디카페인 원두 500g": {
    producerName: "생산자", icon: "coffee", tint: "sand", summary: "", detail: "",
    storyBlocks: [
      { headline: "일반 원두와 다르지 않은 색", body: "디카페인 원두도 일반 원두와 다르지 않은 색과 윤기예요." },
      { headline: "실물 그대로의 모습", body: "원두 색과 크기를 있는 그대로 보여드려요." },
      { headline: "스위스워터 방식으로", body: "화학 용매 없는 스위스워터 방식으로 카페인만 제거했어요." },
      { headline: "향이 날아가지 않게", body: "밀봉 포장으로 원두 향을 그대로 지켜드려요." },
    ],
  },
  "포기김치 3kg": {
    producerName: "생산자", icon: "package", tint: "berry", summary: "", detail: "",
    storyBlocks: [
      { headline: "속까지 꽉 찬 양념", body: "속까지 골고루 밴 빨간 양념을 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "포기 크기와 양념 상태를 있는 그대로 보여드려요." },
      { headline: "직접 담근 정성", body: "재료 손질부터 양념까지 직접 담근 김치예요." },
      { headline: "국물 새지 않게, 안전하게", body: "밀폐 용기에 담아 국물이 새지 않게 보내드려요." },
    ],
  },
  "수제 밑반찬 3종 세트": {
    producerName: "생산자", icon: "package", tint: "berry", summary: "", detail: "",
    storyBlocks: [
      { headline: "정갈하게 담은 3가지 맛", body: "장아찌, 나물, 조림 세 가지 반찬의 색을 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "구성과 담긴 모습을 있는 그대로 보여드려요." },
      { headline: "손맛 그대로 담아", body: "재료 손질부터 조리까지 직접 만든 반찬이에요." },
      { headline: "국물 새지 않게, 안전하게", body: "개별 용기에 담아 국물이 섞이지 않게 보내드려요." },
    ],
  },
  "냉동 손만두 1kg": {
    producerName: "생산자", icon: "package", tint: "berry", summary: "", detail: "",
    storyBlocks: [
      { headline: "손으로 빚은 두툼한 모양", body: "기계가 아닌 손으로 빚어 두툼한 만두피를 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "만두 크기와 모양을 있는 그대로 보여드려요." },
      { headline: "속까지 꽉 채운 고기소", body: "고기와 채소를 넉넉히 채워 빚은 만두예요." },
      { headline: "얼지 않게 뭉치지 않게", body: "급속 냉동해서 하나씩 붙지 않게 포장했어요." },
    ],
  },
  "즉석 육개장 4팩": {
    producerName: "생산자", icon: "package", tint: "berry", summary: "", detail: "",
    storyBlocks: [
      { headline: "얼큰한 붉은 국물", body: "고기와 고사리가 든 얼큰한 국물색을 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "건더기 구성과 국물 색을 있는 그대로 보여드려요." },
      { headline: "우려낸 육수 그대로", body: "정성껏 우려낸 육수로 맛을 냈어요." },
      { headline: "국물 새지 않게, 안전하게", body: "밀봉 파우치에 담아 국물이 새지 않게 보내드려요." },
    ],
  },
  "대나무 칫솔 5개입": {
    producerName: "생산자", icon: "sparkles", tint: "herb", summary: "", detail: "",
    storyBlocks: [
      { headline: "매끈하게 마감된 대나무 손잡이", body: "플라스틱 없이 매끈하게 마감된 손잡이를 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "손잡이 색과 칫솔모 상태를 있는 그대로 보여드려요." },
      { headline: "친환경 소재로만", body: "플라스틱 없이 대나무 소재만으로 만들었어요." },
      { headline: "위생적으로, 안전하게", body: "개별 포장으로 위생적으로 보내드려요." },
    ],
  },
  "친환경 주방세제 리필 1L": {
    producerName: "생산자", icon: "droplet", tint: "herb", summary: "", detail: "",
    storyBlocks: [
      { headline: "맑은 식물성 원액", body: "식물성 원료로 만든 맑은 세제 색을 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "용기와 원액 색을 있는 그대로 보여드려요." },
      { headline: "식물성 원료로만", body: "동물성 원료 없이 식물성 원료로만 만들었어요." },
      { headline: "새지 않게, 안전하게", body: "밀폐 용기에 담아 새지 않게 보내드려요." },
    ],
  },
  "다회용 실리콘 지퍼백 5종": {
    producerName: "생산자", icon: "package", tint: "herb", summary: "", detail: "",
    storyBlocks: [
      { headline: "선명한 색감의 실리콘", body: "일회용 비닐을 대체하는 실리콘 특유의 선명한 색을 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "크기별 구성과 색을 있는 그대로 보여드려요." },
      { headline: "여러 번 다시 쓰도록", body: "씻어서 반복 사용할 수 있는 식품용 실리콘으로 만들었어요." },
      { headline: "찢어지지 않게, 안전하게", body: "각 크기별로 구분해서 안전하게 보내드려요." },
    ],
  },
  "유기농 순면 손수건 5매": {
    producerName: "생산자", icon: "package", tint: "herb", summary: "", detail: "",
    storyBlocks: [
      { headline: "부드러운 순면의 질감", body: "무표백 순면 100% 특유의 부드러운 질감을 확인해보세요." },
      { headline: "실물 그대로의 모습", body: "색감과 크기를 있는 그대로 보여드려요." },
      { headline: "무표백으로 순하게", body: "표백 과정 없이 순면 그대로의 색을 살렸어요." },
      { headline: "구겨지지 않게, 안전하게", body: "가지런히 접어 구김 없이 보내드려요." },
    ],
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
