"use client";

import { useCallback, useEffect, useState } from "react";
import { notFound, useParams, useRouter } from "next/navigation";
import { Check } from "lucide-react";
import DaumPostcode from "react-daum-postcode";
import { PaymentMethodModal } from "@/components/consumer/PaymentMethodModal";
import { GroupBuyArtwork } from "@/components/deal/GroupBuyArtwork";
import { GroupBuyStatusTag } from "@/components/groupbuy/GroupBuyStatusTag";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { TextField } from "@/components/ui/TextField";
import { createMyAddress, listMyAddresses } from "@/lib/api/address";
import {
  cancelGroupBuyParticipation,
  getGroupBuy,
  getGroupBuyStatus,
  getMyGroupBuyParticipation,
  participateInGroupBuy,
} from "@/lib/api/groupBuy";
import { ApiError } from "@/lib/api/http";
import { getProduct } from "@/lib/api/product";
import type {
  BuyerAddressResponse,
  GroupBuyDetailResponse,
  GroupBuyPartMeResponse,
  GroupBuyStatusResponse,
  ProductDetailResponse,
} from "@/lib/api/types";
import { useAuth } from "@/lib/auth/AuthProvider";
import { formatDateTime, formatRemaining, won } from "@/lib/format";
import { resolveCatalogEntry } from "@/lib/groupBuy/seedCatalog";
import { resolveCurrentUnitPrice } from "@/lib/groupBuyPricing";
import { clearPendingParticipation, takePendingParticipation } from "@/lib/payments/pendingParticipation";

const NEW_ADDRESS = "new" as const;

const DESCRIPTION_STORY_BLOCKS = [
  {
    headline: "선명하게 보이는 디테일",
    body: "가까이서 보면 소재와 마감을 그대로 확인할 수 있어요. 사진 그대로의 품질을 약속드립니다.",
  },
  {
    headline: "실물 그대로의 모습",
    body: "화면으로 보는 색감과 실제 받아보시는 상품의 차이를 최소화하기 위해 있는 그대로의 모습을 보여드려요.",
  },
  {
    headline: "정성으로 만든 상품",
    body: "하나하나 정성껏 골라 준비한 상품이에요. 좋은 재료와 꼼꼼한 과정으로 만족스러운 경험을 드리고 싶습니다.",
  },
  {
    headline: "안전하게, 꼼꼼하게",
    body: "배송 중 손상되지 않도록 꼼꼼하게 포장해서 보내드려요. 받으시는 순간까지 신경 썼습니다.",
  },
];

// select 옵션과 결제 확인 창에 공통으로 쓰는 배송지 한 줄 표기
function formatAddressLabel(address: BuyerAddressResponse): string {
  return `[${address.zipcode}] ${address.address}${
    address.addressDetail ? ` ${address.addressDetail}` : ""
  }`;
}

// 공동구매(groupBuyId) 자체가 없을 때만 404 페이지로 보내야 한다. 상태/내 참여/상품 조회의 404는
// 별개 자원의 문제이므로 여기서 일반 에러로 바꿔, 존재하는 공동구매를 "찾을 수 없음"으로 잘못 표시하지 않는다.
function demote404(e: unknown): never {
  if (e instanceof ApiError && e.status === 404) {
    throw new ApiError(500, { code: e.code, message: e.message });
  }
  throw e;
}

export default function DealDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const { status: authStatus } = useAuth();
  const groupBuyId = Number(params.id);

  const [detail, setDetail] = useState<GroupBuyDetailResponse | null>(null);
  const [status, setStatus] = useState<GroupBuyStatusResponse | null>(null);
  const [myPart, setMyPart] = useState<GroupBuyPartMeResponse | null>(null);
  const [product, setProduct] = useState<ProductDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [resourceNotFound, setResourceNotFound] = useState(false);

  // 입력 중 지울 수 있어야 해서 빈 값("")도 허용하고, 실제 검증/전송 시에는 quantityValue(숫자)를 쓴다
  const [quantity, setQuantity] = useState<number | "">(1);
  const [addresses, setAddresses] = useState<BuyerAddressResponse[]>([]);
  // 저장된 배송지 id, 또는 NEW_ADDRESS(직접 입력) 모드
  const [selectedAddressId, setSelectedAddressId] = useState<number | typeof NEW_ADDRESS>(NEW_ADDRESS);
  const [newAddress, setNewAddress] = useState("");
  const [newAddressDetail, setNewAddressDetail] = useState("");
  const [newZipcode, setNewZipcode] = useState("");
  const [isSearchingAddress, setIsSearchingAddress] = useState(false);
  // "참여하기"를 누른 시점에 확정된 buyerAddressId - 결제 확인 창/카드 등록 리다이렉트로 넘긴다
  const [pendingAddressId, setPendingAddressId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [activeTab, setActiveTab] = useState<"description" | "tiers" | "participation">("description");
  const [thumbnailFailed, setThumbnailFailed] = useState(false);
  const [paymentOpen, setPaymentOpen] = useState(false);
  const [addressModalOpen, setAddressModalOpen] = useState(false);
  const quantityValue = typeof quantity === "number" ? quantity : 0;

  const reload = useCallback(async () => {
    // 참여 내역/배송지는 로그인해야만 의미가 있는 개인화 정보다. 비로그인 상태에서 이 API들(auth
    // 필수)을 호출하면 401로 페이지 전체 조회가 실패하므로, 비로그인이면 아예 부르지 않고 빈 값으로 채운다.
    const [detailRes, statusRes, myPartRes, addressesRes] = await Promise.all([
      getGroupBuy(groupBuyId),
      getGroupBuyStatus(groupBuyId).catch(demote404),
      authStatus === "authenticated"
        ? getMyGroupBuyParticipation(groupBuyId).catch(demote404)
        : Promise.resolve<GroupBuyPartMeResponse>({ participated: false, part: null }),
      authStatus === "authenticated"
        // 배송지 조회 실패가 공동구매 화면 전체를 막지 않도록 빈 목록으로 넘어간다
        ? listMyAddresses().catch(() => [] as BuyerAddressResponse[])
        : Promise.resolve<BuyerAddressResponse[]>([]),
    ]);
    const productRes = await getProduct(detailRes.productId).catch(demote404);
    setDetail(detailRes);
    setStatus(statusRes);
    setMyPart(myPartRes);
    setProduct(productRes);
    setAddresses(addressesRes);
    // 저장된 배송지가 있으면 첫 항목을 기본 선택, 없으면 직접 입력 모드
    setSelectedAddressId((current) => {
      if (current !== NEW_ADDRESS && addressesRes.some((a) => a.id === current)) return current;
      return addressesRes[0]?.id ?? NEW_ADDRESS;
    });
  }, [groupBuyId, authStatus]);

  useEffect(() => {
    if (!Number.isFinite(groupBuyId)) return;
    let ignore = false;

    async function load() {
      setLoading(true);
      setLoadError(null);
      setThumbnailFailed(false);
      try {
        await reload();
      } catch (e) {
        if (ignore) return;
        if (e instanceof ApiError && e.status === 404) {
          setResourceNotFound(true);
        } else {
          setLoadError(e instanceof ApiError ? e.message : "공동구매 정보를 불러오지 못했어요.");
        }
      } finally {
        if (!ignore) setLoading(false);
      }
    }

    load();
    return () => {
      ignore = true;
    };
  }, [groupBuyId, reload]);

  // 카드 등록을 마치고 돌아온 경우다. 결제창으로 페이지를 떠나기 전에 보관해 둔 입력값을 되살리고
  // 결제 정보 창을 다시 열어, 사용자가 수량·배송지를 다시 채우지 않고 참여를 이어가게 한다.
  // 배송지는 떠나기 전에 이미 주소록에 확정(buyerAddressId)해 뒀으므로 그 id만 되살리면 된다.
  useEffect(() => {
    if (!Number.isFinite(groupBuyId)) return;
    const pending = takePendingParticipation();
    if (!pending || pending.groupBuyId !== groupBuyId) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- 카드 등록 후 리다이렉트 복귀 시 1회 처리 - 외부 브라우저 이동에 대한 동기화
    setQuantity(pending.quantity);
    setSelectedAddressId(pending.buyerAddressId);
    setPendingAddressId(pending.buyerAddressId);
    setPaymentOpen(true);
  }, [groupBuyId]);

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  function handleCompletePostcode(data: any) {
    setNewZipcode(data.zonecode);
    setNewAddress(data.address);
    setIsSearchingAddress(false);
  }

  // 새 배송지는 입력 즉시 주소록에 저장하고 목록으로 돌아가 선택 상태로 만든다 - "완료하기" 전에
  // 다른 배송지를 눌러버려도 입력해둔 값이 사라지지 않게 하기 위함
  async function handleSaveNewAddress() {
    setActionError(null);
    setSubmitting(true);
    try {
      const created = await createMyAddress({
        address: newAddress.trim(),
        addressDetail: newAddressDetail.trim() || undefined,
        zipcode: newZipcode.trim(),
        isDefault: false,
      });
      setAddresses((prev) => [created, ...prev]);
      setSelectedAddressId(created.id);
      setNewAddress("");
      setNewAddressDetail("");
      setNewZipcode("");
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : "배송지 저장 중 오류가 발생했어요.");
    } finally {
      setSubmitting(false);
    }
  }

  // 배송지 선택 모달의 "완료하기" - 이 시점엔 이미 저장된 배송지 중 하나가 선택돼 있다
  function handleConfirmAddress() {
    if (authStatus !== "authenticated") {
      router.push("/login");
      return;
    }
    if (typeof selectedAddressId !== "number") return;
    setPendingAddressId(selectedAddressId);
    closeAddressModal();
    setPaymentOpen(true);
  }

  // 저장 안 하고 다른 배송지를 고르거나 모달을 닫으면, 입력하다 만 새 배송지 값은 버린다 -
  // 남겨두면 다음에 다시 "새 배송지 입력"을 눌렀을 때 지운 줄 알았던 값이 그대로 보인다
  function resetNewAddressForm() {
    setNewAddress("");
    setNewAddressDetail("");
    setNewZipcode("");
    setActionError(null);
  }

  function closeAddressModal() {
    setAddressModalOpen(false);
    resetNewAddressForm();
  }

  // "참여하기"를 누를 때마다 배송지 선택을 기본 배송지로 초기화한다 - 지난번에 고르다 만
  // 배송지나 "새 배송지 입력" 모드가 다음 참여 시도에 그대로 남아있지 않게 하기 위함
  function openAddressModal() {
    const defaultAddress = addresses.find((a) => a.isDefault) ?? addresses[0];
    setSelectedAddressId(defaultAddress?.id ?? NEW_ADDRESS);
    resetNewAddressForm();
    setAddressModalOpen(true);
  }

  async function handleParticipate() {
    if (pendingAddressId === null) return;
    setActionError(null);
    setActionMessage(null);
    setSubmitting(true);
    try {
      await participateInGroupBuy(groupBuyId, {
        quantity: quantityValue,
        buyerAddressId: pendingAddressId,
      });
      clearPendingParticipation();
      setPaymentOpen(false);
      await reload();
      setActionMessage("참여가 완료됐어요.");
    } catch (e) {
      // 실패 사유는 상세 화면의 배너로 보여주므로, 그 배너를 가리지 않도록 창을 닫는다
      setPaymentOpen(false);
      setActionError(e instanceof ApiError ? e.message : "참여 처리 중 오류가 발생했어요.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCancelParticipation(partId: number) {
    setActionError(null);
    setActionMessage(null);
    setSubmitting(true);
    try {
      await cancelGroupBuyParticipation(groupBuyId, partId);
      await reload();
      setActionMessage("참여를 취소했어요.");
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : "참여 취소 중 오류가 발생했어요.");
    } finally {
      setSubmitting(false);
    }
  }

  if (!Number.isFinite(groupBuyId) || resourceNotFound) {
    notFound();
  }

  if (loading) {
    return <div className="p-9 text-sm text-wb-secondary">불러오는 중...</div>;
  }

  if (loadError || !detail || !status || !product) {
    return (
      <div className="mx-auto max-w-3xl px-6 py-9">
        <Banner tone="error">{loadError ?? "공동구매 정보를 불러오지 못했어요."}</Banner>
      </div>
    );
  }

  const catalog = resolveCatalogEntry(detail.productName);
  const storyBlocks = catalog.storyBlocks ?? DESCRIPTION_STORY_BLOCKS;
  const newAddressValid = /^\d{5}$/.test(newZipcode.trim()) && newAddress.trim() !== "";
  // 배송지는 "참여하기" 클릭 후 뜨는 별도 모달에서 고르므로, 여기서는 수량/상태만 확인한다.
  // 비로그인 사용자는 수량을 채우지 않아도 참여 버튼을 누를 수 있어야 한다 - 클릭 시 로그인으로 보낸다
  const canParticipate =
    authStatus !== "authenticated"
      ? status.status === "ONGOING"
      : status.status === "ONGOING" && !myPart?.participated && quantityValue >= 1;
  const myPartAddress =
    myPart?.part?.buyerAddressId != null
      ? addresses.find((a) => a.id === myPart.part!.buyerAddressId)
      : undefined;
  const pendingAddress =
    pendingAddressId != null ? addresses.find((a) => a.id === pendingAddressId) : undefined;
  const currentPrice = resolveCurrentUnitPrice(detail.priceTiers, status.currentQuantity);
  const achievementRate =
    detail.maxQuantity === 0 ? 0 : Math.round((status.currentQuantity / detail.maxQuantity) * 100);

  const sortedTiers = [...detail.priceTiers].sort((a, b) => a.thresholdQuantity - b.thresholdQuantity);
  const reachedTiers = sortedTiers.filter((t) => t.thresholdQuantity <= status.currentQuantity);
  const activeTierOrder = reachedTiers.at(-1)?.tierOrder ?? sortedTiers[0]?.tierOrder;

  const TABS = [
    { key: "description" as const, label: "상세 설명" },
    { key: "tiers" as const, label: "가격 구간" },
    { key: "participation" as const, label: "내 참여" },
  ];

  return (
    <div className="mx-auto max-w-6xl px-6 py-9">
      <div className="grid gap-8 lg:grid-cols-[1fr_360px]">
        <div className="min-w-0 space-y-6">
          <div className="space-y-1">
            <p className="text-xs text-wb-secondary">
              {catalog.producerName} · {detail.productCategory}
            </p>
            <h1 className="text-2xl">{detail.title}</h1>
          </div>

          {product.thumbnailUrl && !thumbnailFailed ? (
            // eslint-disable-next-line @next/next/no-img-element -- 판매자가 등록한 외부 썸네일 URL이라 next/image 최적화 대상이 아님
            <img
              src={product.thumbnailUrl}
              alt={detail.title}
              className="aspect-[4/3] w-full rounded-2xl object-cover"
              onError={() => setThumbnailFailed(true)}
            />
          ) : (
            <GroupBuyArtwork entry={catalog} className="aspect-[4/3] w-full" />
          )}

          <div className="flex gap-1 border-b border-wb-line">
            {TABS.map((tab) => (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                className={`border-b-2 px-4 py-3 text-sm font-semibold transition-colors ${
                  activeTab === tab.key
                    ? "border-wb-green text-wb-green"
                    : "border-transparent text-wb-secondary hover:text-wb-ink"
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {activeTab === "description" && (
            <div className="space-y-14">
              {(catalog.summary || product.description || catalog.detail) && (
                <div className="space-y-3">
                  {catalog.summary && <p className="text-base">{catalog.summary}</p>}
                  {(product.description || catalog.detail) && (
                    <p className="text-sm text-wb-secondary">{product.description || catalog.detail}</p>
                  )}
                  <p className="text-sm text-wb-secondary">
                    모집 기간 {formatDateTime(detail.startAt)} ~ {formatDateTime(detail.endAt)}
                  </p>
                </div>
              )}

              {product.descriptionImageUrls.map((url, index) => {
                const block = storyBlocks[index];
                return (
                  <div
                    key={`${url}-${index}`}
                    className={`space-y-6 rounded-3xl p-8 ${index % 2 === 0 ? "bg-wb-canvas" : ""}`}
                  >
                    {block && (
                      <div className="mx-auto max-w-xl space-y-2 text-center">
                        <h3 className="text-2xl font-bold text-wb-green">{block.headline}</h3>
                        <p className="text-base text-wb-secondary">{block.body}</p>
                      </div>
                    )}
                    {/* eslint-disable-next-line @next/next/no-img-element -- 판매자가 등록한 외부 이미지 URL이라 next/image 최적화 대상이 아님 */}
                    <img
                      src={url}
                      alt={`${detail.title} 상세 이미지 ${index + 1}`}
                      className="aspect-[16/10] w-full rounded-2xl object-cover shadow-md"
                    />
                  </div>
                );
              })}
            </div>
          )}

          {activeTab === "tiers" && (
            <div className="space-y-3">
              <p className="text-sm text-wb-secondary">참여 수량이 늘어날수록 가격이 내려가요.</p>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                {sortedTiers.map((tier) => {
                  const active = tier.tierOrder === activeTierOrder;
                  return (
                    <div
                      key={tier.tierOrder}
                      className={`rounded-xl p-4 ${active ? "bg-wb-light-green/60" : "bg-wb-canvas"}`}
                    >
                      <p className={`text-xs font-semibold ${active ? "text-wb-green" : "text-wb-secondary"}`}>
                        {tier.thresholdQuantity.toLocaleString("ko-KR")}개부터
                      </p>
                      <p className="mt-1.5 text-lg font-bold">{won(tier.unitPrice)}</p>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {activeTab === "participation" &&
            (myPart?.participated && myPart.part ? (
              <div className="space-y-3">
                <p className="text-sm text-wb-secondary">
                  {myPart.part.quantity.toLocaleString("ko-KR")}개 ·{" "}
                  {myPart.part.appliedPrice !== null ? (
                    <>확정 단가 {won(myPart.part.appliedPrice)}</>
                  ) : (
                    <>
                      현재 예상가 {currentPrice !== null ? won(currentPrice) : "-"}
                      <span className="text-xs"> (공동구매 성사 시 전원 동일한 최종가로 확정돼요)</span>
                    </>
                  )}
                </p>
                {myPartAddress && (
                  <p className="text-xs text-wb-secondary">{formatAddressLabel(myPartAddress)}</p>
                )}
                {status.status === "ONGOING" && myPart.part.status === "CONFIRMED" && (
                  <Button
                    variant="secondary"
                    loading={submitting}
                    onClick={() => handleCancelParticipation(myPart.part!.id)}
                  >
                    참여 취소
                  </Button>
                )}
              </div>
            ) : (
              <p className="text-sm text-wb-secondary">
                아직 참여하지 않았어요. 오른쪽에서 수량을 정하고 참여해보세요.
              </p>
            ))}

        </div>

        <aside className="space-y-4 lg:sticky lg:top-20 lg:self-start">
          <div className="space-y-4 rounded-2xl border border-wb-line bg-wb-surface p-6">
            <GroupBuyStatusTag status={status.status} />

            <div>
              <p className="text-xs text-wb-secondary">현재 가격</p>
              <div className="flex items-baseline gap-2">
                <p className="text-3xl font-bold">{currentPrice !== null ? won(currentPrice) : "-"}</p>
                {currentPrice !== null && product.startPrice > currentPrice && (
                  <p className="text-sm text-wb-secondary line-through">{won(product.startPrice)}</p>
                )}
              </div>
            </div>

            <ProgressBar value={detail.maxQuantity === 0 ? 0 : status.currentQuantity / detail.maxQuantity} />

            <div className="grid grid-cols-3 gap-2 text-center text-xs">
              <div>
                <p className="text-sm font-bold text-wb-green">{achievementRate}%</p>
                <p className="text-wb-secondary">달성률</p>
              </div>
              <div>
                <p className="text-sm font-bold">{formatRemaining(status.remainingSeconds)}</p>
                <p className="text-wb-secondary">남은 시간</p>
              </div>
              <div>
                <p className="text-sm font-bold">{status.participantCount.toLocaleString("ko-KR")}명</p>
                <p className="text-wb-secondary">참여자</p>
              </div>
            </div>

            <dl className="space-y-1.5 border-t border-wb-line pt-3 text-xs">
              <div className="flex justify-between">
                <dt className="text-wb-secondary">참여 수량</dt>
                <dd>
                  {status.currentQuantity.toLocaleString("ko-KR")} / {detail.maxQuantity.toLocaleString("ko-KR")}개
                </dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-wb-secondary">최소 성사 수량</dt>
                <dd>{detail.minQuantity.toLocaleString("ko-KR")}개</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="shrink-0 text-wb-secondary">모집 기간</dt>
                <dd className="text-right">
                  {formatDateTime(detail.startAt)} ~ {formatDateTime(detail.endAt)}
                </dd>
              </div>
            </dl>

            <div className="space-y-3 border-t border-wb-line pt-4">
              {myPart?.participated && myPart.part ? (
                <div className="space-y-2">
                  <p className="text-sm font-semibold text-wb-green">이미 참여했어요</p>
                  <p className="text-xs text-wb-secondary">{myPart.part.quantity.toLocaleString("ko-KR")}개 참여 중</p>
                  {status.status === "ONGOING" && myPart.part.status === "CONFIRMED" && (
                    <Button
                      variant="secondary"
                      className="w-full"
                      loading={submitting}
                      onClick={() => handleCancelParticipation(myPart.part!.id)}
                    >
                      참여 취소
                    </Button>
                  )}
                </div>
              ) : (
                <div className="space-y-3">
                  <TextField
                    label="참여 수량"
                    inputMode="numeric"
                    value={String(quantity)}
                    onChange={(e) => {
                      const digitsOnly = e.target.value.replace(/[^0-9]/g, "");
                      setQuantity(digitsOnly === "" ? "" : Number(digitsOnly));
                    }}
                  />

                  <Button
                    className="w-full"
                    disabled={!canParticipate}
                    onClick={() => {
                      if (authStatus !== "authenticated") {
                        router.push("/login");
                        return;
                      }
                      openAddressModal();
                    }}
                  >
                    {status.status !== "ONGOING" ? "참여할 수 없어요" : "참여하기"}
                  </Button>
                </div>
              )}
              {actionMessage && <Banner tone="success">{actionMessage}</Banner>}
              {actionError && <Banner tone="error">{actionError}</Banner>}
            </div>
          </div>

          <div className="rounded-2xl border border-wb-line bg-wb-surface p-4">
            <p className="text-sm font-semibold">{catalog.producerName}</p>
            <p className="text-xs text-wb-secondary">생산자</p>
          </div>
        </aside>
      </div>

      <Modal open={addressModalOpen} onClose={closeAddressModal} title="배송지 선택" width="440px">
        <div className="space-y-3">
          <div className="space-y-2">
            {addresses.map((a) => {
              const active = selectedAddressId === a.id;
              return (
                <button
                  key={a.id}
                  type="button"
                  onClick={() => {
                    setSelectedAddressId(a.id);
                    resetNewAddressForm();
                  }}
                  className={`flex w-full items-start gap-3 rounded-xl border p-3 text-left text-sm transition-colors ${
                    active ? "border-wb-green bg-wb-light-green/30" : "border-wb-line hover:bg-wb-canvas"
                  }`}
                >
                  <span
                    className={`mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded-full border-2 ${
                      active ? "border-wb-green bg-wb-green" : "border-wb-line"
                    }`}
                  >
                    {active && <Check className="h-2.5 w-2.5 text-white" strokeWidth={3} />}
                  </span>
                  <span className="space-y-0.5">
                    <span className="flex items-center gap-1.5 font-semibold">
                      [{a.zipcode}]
                      {a.isDefault && (
                        <span className="rounded-full bg-wb-green px-1.5 py-0.5 text-[10px] font-semibold text-white">
                          기본
                        </span>
                      )}
                    </span>
                    <span className="block text-wb-secondary">
                      {a.address} {a.addressDetail}
                    </span>
                  </span>
                </button>
              );
            })}

            <button
              type="button"
              onClick={() => setSelectedAddressId(NEW_ADDRESS)}
              className={`flex w-full items-center justify-center rounded-xl border border-dashed p-3 text-sm font-semibold transition-colors ${
                selectedAddressId === NEW_ADDRESS
                  ? "border-wb-green bg-wb-light-green/30 text-wb-green"
                  : "border-wb-line text-wb-secondary hover:bg-wb-canvas"
              }`}
            >
              + 새 배송지 입력
            </button>
          </div>

          {selectedAddressId === NEW_ADDRESS && (
            <div className="space-y-3 border-t border-wb-line pt-3">
              <div className="flex items-end gap-2">
                <div className="flex-1">
                  <TextField label="우편번호" value={newZipcode} readOnly />
                </div>
                <Button
                  type="button"
                  variant="secondary"
                  className="h-11"
                  onClick={() => setIsSearchingAddress(true)}
                >
                  우편번호 검색
                </Button>
              </div>
              <TextField label="배송지 주소" value={newAddress} readOnly />
              <TextField
                label="상세주소 (선택)"
                value={newAddressDetail}
                onChange={(e) => setNewAddressDetail(e.target.value)}
              />
              <Button
                type="button"
                variant="secondary"
                className="w-full"
                disabled={!newAddressValid}
                loading={submitting}
                onClick={handleSaveNewAddress}
              >
                배송지 저장
              </Button>
            </div>
          )}

          {actionError && <Banner tone="error">{actionError}</Banner>}

          {selectedAddressId !== NEW_ADDRESS && (
            <Button
              className="w-full"
              disabled={typeof selectedAddressId !== "number"}
              onClick={handleConfirmAddress}
            >
              완료하기
            </Button>
          )}
        </div>
      </Modal>

      {isSearchingAddress && (
        <Modal open onClose={() => setIsSearchingAddress(false)} title="주소 검색" width="480px">
          <DaumPostcode onComplete={handleCompletePostcode} autoClose={false} style={{ height: "400px", width: "100%" }} />
        </Modal>
      )}

      <PaymentMethodModal
        open={paymentOpen}
        onClose={() => setPaymentOpen(false)}
        title={detail.title}
        unitPrice={currentPrice}
        pending={{ groupBuyId, quantity: quantityValue, buyerAddressId: pendingAddressId ?? 0 }}
        addressLabel={pendingAddress ? formatAddressLabel(pendingAddress) : null}
        submitting={submitting}
        onConfirm={handleParticipate}
      />
    </div>
  );
}
