export type Role = "ADMIN" | "SELLER" | "BUYER";

export type OAuthProvider = "GOOGLE" | "KAKAO";

export type LoginRequest = {
  email: string;
  password: string;
};

export type LoginResponse = {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresIn: number;
  deviceId: string;
};

export type ReissueResponse = {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresIn: number;
};

export type SignupRequest = {
  email: string;
  password: string;
  name: string;
};

export type SignupResponse = {
  memberId: number;
  email: string;
  name: string;
  role: Role;
};

export type MemberResponse = {
  memberId: number;
  email: string;
  name: string;
  profileImageUrl: string | null;
  role: Role;
};

export type UpdateMemberRequest = {
  name: string;
  profileImageUrl?: string;
};

export type SocialAccountsResponse = {
  providers: OAuthProvider[];
};

export type SocialLinkResponse = {
  redirectUrl: string;
};

// issuedAt/lastUsedAt은 epoch seconds (밀리초 아님)
export type DeviceSessionResponse = {
  deviceId: string;
  issuedAt: number;
  lastUsedAt: number;
};

export type SellerApplyRequest = {
  bankCode: string;
  bankName: string;
  accountNumber: string;
  accountHolder: string;
  companyName?: string;
};

export type SellerSignupRequest = SignupRequest & SellerApplyRequest;

export type ErrorResponse = {
  code: string;
  message: string;
};

export type GroupBuyStatus = "READY" | "ONGOING" | "SUCCESS" | "FAILED" | "CANCELED";

export type GroupBuyPartStatus = "PENDING" | "CONFIRMED" | "CANCELED";

export type GroupBuyPriceTier = {
  tierOrder: number;
  thresholdQuantity: number;
  unitPrice: number;
};

export type GroupBuyCreateRequest = {
  productId: number;
  title: string;
  startAt: string;
  endAt: string;
  minQuantity: number;
  maxQuantity: number;
  priceTiers: GroupBuyPriceTier[];
};

export type GroupBuyUpdateRequest = {
  title?: string;
  endAt?: string;
};

export type GroupBuyDetailResponse = {
  id: number;
  productId: number;
  producerId: number;
  title: string;
  status: GroupBuyStatus;
  startAt: string;
  endAt: string;
  minQuantity: number;
  maxQuantity: number;
  priceTiers: GroupBuyPriceTier[];
  createdAt: string;
};

export type GroupBuyStatusResponse = {
  id: number;
  status: GroupBuyStatus;
  currentQuantity: number;
  remainingQuantity: number;
  participantCount: number;
  remainingSeconds: number;
};

export type GroupBuySummaryResponse = {
  id: number;
  productId: number;
  producerId: number;
  title: string;
  status: GroupBuyStatus;
  startAt: string;
  endAt: string;
  currentQuantity: number;
  maxQuantity: number;
};

export type GroupBuyPartCreateRequest = {
  quantity: number;
};

// appliedPrice는 공동구매가 성사되기 전까지 null - 백엔드는 참여 시점에 가격을 계산/저장하지 않고
// 성사되는 순간에만 참여자 전원에게 동일한 최종가를 채운다
export type GroupBuyPartResponse = {
  id: number;
  groupBuyId: number;
  quantity: number;
  appliedPrice: number | null;
  status: GroupBuyPartStatus;
  createdAt: string;
};

export type GroupBuyPartMeResponse = {
  participated: boolean;
  part: GroupBuyPartResponse | null;
};

// 백엔드가 Page<T>를 그대로 직렬화하지 않고 Spring Data의 PagedModel(@EnableSpringDataWebSupport(VIA_DTO))로
// 응답하므로, 페이지 메타데이터는 최상위가 아니라 page 필드 아래에 중첩된다
export type PageResponse<T> = {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
};
