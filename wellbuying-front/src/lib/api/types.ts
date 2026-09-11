export type Role = "ADMIN" | "SELLER" | "BUYER";

export type OAuthProvider = "GOOGLE" | "KAKAO";

export type LoginRequest = {
  email: string;
  password: string;
};

// refreshToken은 httpOnly 쿠키(Set-Cookie)로만 내려가고 응답 JSON에는 담기지 않는다(phase25)
export type LoginResponse = {
  accessToken: string;
  accessTokenExpiresIn: number;
  deviceId: string;
};

export type ReissueResponse = {
  accessToken: string;
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

export type SellerStatus = "PENDING" | "APPROVED" | "SUSPENDED" | "REJECTED";

export type SellerInfoResponse = {
  id: number;
  memberId: number;
  status: SellerStatus;
  bankName: string;
  companyName: string | null;
  createdAt: string;
};

export type MemberStatus = "ACTIVE" | "DORMANT" | "WITHDRAWN";

// GET /api/admin/members 응답 - 관리자 회원 목록 조회
// sellerId/sellerStatus는 role이 SELLER인 회원만 값이 채워짐 - 그 외에는 null
export type MemberSummaryResponse = {
  id: number;
  email: string;
  name: string;
  role: Role;
  status: MemberStatus;
  phoneNumber: string | null;
  createdAt: string;
  sellerId: number | null;
  sellerStatus: SellerStatus | null;
};

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
  productName: string;
  productCategory: string;
  producerId: number;
  title: string;
  status: GroupBuyStatus;
  startAt: string;
  endAt: string;
  minQuantity: number;
  maxQuantity: number;
  priceTiers: GroupBuyPriceTier[];
  createdAt: string;
  suspended: boolean;
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
  productName: string;
  productCategory: string;
  producerId: number;
  title: string;
  status: GroupBuyStatus;
  startAt: string;
  endAt: string;
  currentQuantity: number;
  maxQuantity: number;
  suspended: boolean;
  viewCount: number;
  createdAt: string;
};

export type GroupBuyPartCreateRequest = {
  quantity: number;
  // 회원 주소록(GET /api/members/me/addresses)에 등록된 배송지 항목의 ID
  buyerAddressId: number;
};

export type GroupBuySuspensionStatus = "PENDING" | "APPROVED" | "REJECTED";

export type GroupBuySuspensionRequestCreateRequest = {
  reason?: string;
};

export type GroupBuySuspensionRequestResponse = {
  id: number;
  groupBuyId: number;
  groupBuyTitle: string;
  requesterId: number;
  reason: string | null;
  status: GroupBuySuspensionStatus;
  requestedAt: string;
  decidedAt: string | null;
};

export type ProductDetailResponse = {
  id: number;
  productName: string;
  description: string | null;
  startPrice: number;
  thumbnailUrl: string | null;
  available: boolean;
};

export type ProductCreateRequest = {
  categoryId: number;
  productName: string;
  description?: string;
  startPrice: number;
  thumbnailUrl?: string;
};

export type ProductStatus = "PENDING" | "APPROVED" | "REJECTED";

// GET /api/products/mine 응답 - 판매자 본인이 등록한 상품(상태 무관) 조회
export type ProductMineResponse = {
  id: number;
  productName: string;
  startPrice: number;
  thumbnailUrl: string | null;
  categoryId: number;
  description: string | null;
  status: ProductStatus;
  createdAt: string;
};

// GET /api/admin/products 응답 - 관리자 상품 심사 목록 조회
export type ProductAdminResponse = {
  id: number;
  sellerId: number;
  categoryId: number;
  productName: string;
  startPrice: number;
  thumbnailUrl: string | null;
  status: ProductStatus;
  createdAt: string;
};

export type CategoryTreeResponse = {
  id: number;
  categoryName: string;
  sortOrder: number;
  children: CategoryTreeResponse[];
};

// 관리자 카테고리 CRUD 응답 - parentId 포함 (공개용 CategoryTreeResponse와 구분)
export type CategoryResponse = {
  id: number;
  parentId: number | null;
  categoryName: string;
  sortOrder: number;
};

// parentId가 null이면 최상위(1뎁스), 값이 있으면 해당 부모의 하위(2뎁스)
export type CategoryCreateRequest = {
  parentId: number | null;
  categoryName: string;
  sortOrder: number;
};

export type CategoryUpdateRequest = {
  categoryName: string;
  sortOrder: number;
};


// 백엔드가 Slice<T>를 직렬화한 형태 - Page와 달리 총 개수를 세지 않아 page 메타데이터가 없다
export type SliceResponse<T> = {
  content: T[];
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
};

// appliedPrice는 공동구매가 성사되기 전까지 null - 백엔드는 참여 시점에 가격을 계산/저장하지 않고
// 성사되는 순간에만 참여자 전원에게 동일한 최종가를 채운다
export type GroupBuyPartResponse = {
  id: number;
  groupBuyId: number;
  quantity: number;
  appliedPrice: number | null;
  status: GroupBuyPartStatus;
  buyerAddressId: number | null;
  createdAt: string;
};

export type GroupBuyPartMeResponse = {
  participated: boolean;
  part: GroupBuyPartResponse | null;
};

// 회원 배송지 주소록 - 공동구매 참여 시 buyerAddressId로 참조한다
export type BuyerAddressResponse = {
  id: number;
  address: string;
  addressDetail: string | null;
  zipcode: string;
  isDefault: boolean;
  createdAt: string;
};

export type BuyerAddressCreateRequest = {
  address: string;
  addressDetail?: string;
  // 새 우편번호 체계 - 숫자 5자리 고정
  zipcode: string;
  // 최초 등록이거나 true면 기본 배송지로 지정 - 기존 기본 배송지는 자동 해제된다
  isDefault: boolean;
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

export type NotificationType = "GROUP_BUY_COMPLETED" | "GROUP_BUY_FAILED" | "PAYMENT_COMPLETED" | "PAYMENT_FAILED";

export type NotificationResponse = {
  id: number;
  type: NotificationType;
  groupBuyId: number;
  productId: number | null;
  message: string;
  read: boolean;
  createdAt: string;
};

export type NotificationUnreadCountResponse = {
  count: number;
};

export type BillingKeyAuthRequestResponse = {
  customerKey: string;
};

// 빌링키 자체는 응답에 실리지 않는다 - 등록 여부와 표시용 카드 정보만 온다
export type BillingKeyResponse = {
  registered: boolean;
  cardCompany: string | null;
  cardLast4: string | null;
};

// 주문/결제 내역 (order 도메인 - GET /api/orders/me)
// 배송 상태(PREPARING~DELIVERED)는 shipping 도메인이 채우기 전까지 나타나지 않는다
export type OrderStatus =
  | "PENDING"
  | "PAID"
  | "PAYMENT_FAILED"
  | "PREPARING"
  | "SHIPPING"
  | "DELIVERED"
  | "CONFIRMED"
  | "CANCELED";

export type PaymentStatus = "READY" | "APPROVED" | "CANCELED" | "FAILED" | "REFUNDED";

export type OrderSummaryResponse = {
  orderId: string;
  groupBuyId: number | null;
  groupBuyTitle: string;
  productName: string;
  thumbnailUrl: string | null;
  quantity: number;
  totalPrice: number;
  status: OrderStatus;
  createdAt: string;
};

export type OrderDetailResponse = {
  orderId: string;
  groupBuyId: number | null;
  groupBuyTitle: string;
  productName: string;
  thumbnailUrl: string | null;
  quantity: number;
  // group_buy_part.appliedPrice - 결제된 건이면 항상 채워지지만 이론상 성사 전 상태 대비 nullable
  unitPrice: number | null;
  totalPrice: number;
  status: OrderStatus;
  shippingAddress: string;
  pgProvider: string | null;
  pgTransactionId: string | null;
  paymentStatus: PaymentStatus | null;
  approvedAt: string | null;
  createdAt: string;
};

export type ProfileImageUploadUrlRequest = {
  contentType: string;
};

export type ProfileImageUploadUrlResponse = {
  uploadUrl: string;
  profileImageUrl: string;
};

// 정산 확정 내역 (settlement 도메인 - GET /api/admin/settlements, 관리자 전용)
// CONFIRMED = 정산액 확정, 지급 대기 / PAID = 실제 지급 완료 (아직 지급 실행 연동 전이라 항상 CONFIRMED)
export type SettlementStatus = "CONFIRMED" | "PAID";

export type SettlementResponse = {
  settlementId: number;
  groupBuyId: number;
  // 공동구매가 조회 시점에 조회되지 않으면(드묾) null
  groupBuyTitle: string | null;
  producerId: number;
  producerName: string | null;
  itemCount: number;
  totalSales: number;
  platformFee: number;
  payout: number;
  status: SettlementStatus;
  confirmedAt: string;
};

// 판매자 정산 목록의 필터/표시 상태 (GET /api/settlements/me) - 관리자용 SettlementStatus와는 별개
export type SettlementListStatus = "PENDING" | "COMPLETED";

// 월별 정산 목록 한 건. finalizedAt(공동구매 성사월) 기준으로 월에 귀속된다.
// PENDING이면 settlementId/confirmedAt이 null (아직 확정 전이라 존재하지 않음)
export type SettlementListItemResponse = {
  settlementId: number | null;
  groupBuyId: number;
  groupBuyTitle: string | null;
  producerId: number;
  producerName: string | null;
  itemCount: number;
  totalSales: number;
  platformFee: number;
  payout: number;
  status: SettlementListStatus;
  finalizedAt: string | null;
  confirmedAt: string | null;
};

export type SettlementTrendGranularity = "MONTHLY" | "WEEKLY";

// groupBuyCount는 그 구간에 결제가 있었던 "서로 다른 공동구매" 건수 (참여자 수가 아님 -
// 한 공동구매에 참여자가 여럿이어도 성사 건수는 1건)
export type SettlementTrendPointResponse = {
  periodStart: string;
  totalSales: number;
  groupBuyCount: number;
};

// 이번 달 요약 카드 3개(이번 달 매출 / 정산 대기 중 / 이번 달 정산 완료)에 필요한 값을 한 번에 담는다
export type SettlementMonthlySummaryResponse = {
  yearMonth: string;
  thisMonthTotalSales: number;
  thisMonthItemCount: number;
  previousMonthTotalSales: number;
  pendingAmount: number;
  pendingItemCount: number;
  thisMonthSettledAmount: number;
  thisMonthSettledItemCount: number;
};

// PENDING 건 상세 - 확정 참여자 중 몇 명이 결제까지 끝냈는지
export type SettlementProgressResponse = {
  totalParticipants: number;
  paidParticipants: number;
};

// COMPLETED 건 상세 - 결제한 참여자 한 명
export type SettlementParticipantResponse = {
  memberId: number;
  memberName: string | null;
  amount: number;
  paidAt: string;
};

export type SearchSortType = "RELEVANCE" | "POPULAR";

export type ProductSearchResponse = {
  id: number;
  productName: string;
  startPrice: number;
  thumbnailUrl: string;
  viewCount: number;
  hasActiveGroupBuy: boolean;
  groupBuyId: number | null;
  groupBuyStatus: string | null;
  currentUnitPrice: number | null;
  currentQuantity: number | null;
  targetQuantity: number | null;
  maxQuantity: number | null;
  endAt: string | null;
};

export type CursorPageResponse<T> = {
  content: T[];
  nextCursor: string | null;
  hasNext: boolean;
};

export type ProductSortType = "LATEST" | "POPULAR" | "PRICE_ASC" | "PRICE_DESC";

export type ProductSummaryResponse = {
  id: number;
  productName: string;
  startPrice: number;
  thumbnailUrl: string | null;
  viewCount: number;
};

export type ProductUpdateRequest = {
  categoryId: number;
  productName: string;
  description?: string;
  startPrice: number;
  thumbnailUrl?: string;
};

export type ProductDeleteRequest = {
  reason: string;
};

// GET /api/admin/products/deleted 응답 - 삭제된 상품 이력 조회
export type ProductDeletedAdminResponse = {
  id: number;
  sellerId: number;
  productName: string;
  deletedAt: string;
  deletedBy: number;
  deleteReason: string;
};

// GET /api/products/autocomplete 응답 - 상품명 자동완성
export type ProductAutocompleteResponse = {
  id: number;
  productName: string;
};
