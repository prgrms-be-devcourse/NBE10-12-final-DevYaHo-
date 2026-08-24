import type {
  AdminMember,
  Deal,
  ProductSubmission,
  SettlementRecord,
} from "@/lib/mock/types";

export const SAMPLE_DEALS: Deal[] = [
  {
    id: "deal-detergent",
    title: "매일 쓰는 유기농 주방 세제",
    producer: "푸른살림 연구소",
    category: "생활",
    summary: "식물 유래 성분 98%, 필요한 만큼만 만들어 낭비를 줄여요.",
    detail:
      "자극적인 향과 불필요한 포장을 덜어낸 주방 세제입니다. 주문 수량만큼 생산해 재고 폐기를 줄이고, 수량이 늘어날수록 용기와 물류 단가가 낮아집니다.",
    icon: "droplet",
    tint: "herb",
    daysLeft: 4,
    targetPeople: 1000,
    tiers: [
      { minimumPeople: 100, price: 15000, cost: 12000 },
      { minimumPeople: 500, price: 12900, cost: 9900 },
      { minimumPeople: 1000, price: 10900, cost: 7900 },
    ],
  },
  {
    id: "deal-peach",
    title: "산지에서 바로 오는 천도복숭아",
    producer: "김해 과수원 박정우",
    category: "식품",
    summary: "수확일에 맞춰 보내는 새콤달콤한 제철 복숭아 2kg.",
    detail:
      "주문이 모인 만큼 수확해 선별하고 바로 발송합니다. 중간 도매 단계를 줄여 생산자의 수익은 지키고 소비자의 부담은 낮췄습니다.",
    icon: "leaf",
    tint: "citrus",
    daysLeft: 2,
    targetPeople: 500,
    tiers: [
      { minimumPeople: 50, price: 24000, cost: 18000 },
      { minimumPeople: 300, price: 20000, cost: 14000 },
      { minimumPeople: 500, price: 18500, cost: 12500 },
    ],
  },
  {
    id: "deal-tshirt",
    title: "오래 입는 코튼 베이직 티셔츠",
    producer: "스튜디오 모노",
    category: "패션",
    summary: "원단과 봉제 공정을 모두 공개한 30수 코튼 티셔츠.",
    detail:
      "유행을 타지 않는 실루엣과 촘촘한 봉제로 오래 입을 수 있게 만들었습니다. 수량에 따라 원단 발주 단가가 낮아지는 구조를 그대로 가격에 반영합니다.",
    icon: "shirt",
    tint: "ocean",
    daysLeft: 7,
    targetPeople: 300,
    tiers: [
      { minimumPeople: 50, price: 29000, cost: 22000 },
      { minimumPeople: 200, price: 25000, cost: 18000 },
      { minimumPeople: 300, price: 23000, cost: 16000 },
    ],
  },
];

export const SAMPLE_PARTICIPANT_COUNTS: Record<string, number> = {
  "deal-detergent": 684,
  "deal-peach": 328,
  "deal-tshirt": 91,
};

export const SAMPLE_PRODUCER_DEAL_IDS = ["deal-detergent"];

export const SAMPLE_SUBMISSIONS: ProductSubmission[] = [
  {
    id: "sub-1",
    title: "제주 밭에서 온 미니 단호박",
    producer: "올곧은 농장",
    category: "식품",
    submittedAt: "오늘 09:42",
    proposedPrice: 17800,
  },
  {
    id: "sub-2",
    title: "리필 가능한 고체 치약",
    producer: "제로웨이스트 랩",
    category: "생활",
    submittedAt: "어제 16:18",
    proposedPrice: 9900,
  },
  {
    id: "sub-3",
    title: "국내 봉제 린넨 앞치마",
    producer: "담담한 작업실",
    category: "패션",
    submittedAt: "8월 19일",
    proposedPrice: 32000,
  },
];

export const SAMPLE_SETTLEMENTS: SettlementRecord[] = [
  {
    id: "settle-1",
    producer: "푸른살림 연구소",
    groupBuyTitle: "유기농 주방 세제 7월 공동구매",
    sales: 12640000,
    platformFee: 632000,
    payout: 12008000,
  },
  {
    id: "settle-2",
    producer: "김해 과수원 박정우",
    groupBuyTitle: "제철 황도 복숭아",
    sales: 8950000,
    platformFee: 447500,
    payout: 8502500,
  },
  {
    id: "settle-3",
    producer: "스튜디오 모노",
    groupBuyTitle: "코튼 베이직 티셔츠 1차",
    sales: 6750000,
    platformFee: 337500,
    payout: 6412500,
  },
];

export const SAMPLE_ADMIN_MEMBERS: AdminMember[] = [
  {
    id: "member-1",
    nickname: "정직한소비",
    email: "honest@sample.kr",
    joinedAt: "2026.08.21",
    participationCount: 4,
    status: "정상",
  },
  {
    id: "member-2",
    nickname: "초록장바구니",
    email: "green@sample.kr",
    joinedAt: "2026.08.19",
    participationCount: 7,
    status: "정상",
  },
  {
    id: "member-3",
    nickname: "가격탐험가",
    email: "price@sample.kr",
    joinedAt: "2026.08.17",
    participationCount: 2,
    status: "정상",
  },
  {
    id: "member-4",
    nickname: "새로운구매자",
    email: "new@sample.kr",
    joinedAt: "2026.08.16",
    participationCount: 0,
    status: "휴면 예정",
  },
];
