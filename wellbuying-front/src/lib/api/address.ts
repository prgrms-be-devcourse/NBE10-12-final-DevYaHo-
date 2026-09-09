import { http } from "@/lib/api/http";
import type { BuyerAddressCreateRequest, BuyerAddressResponse } from "@/lib/api/types";

// 회원 배송지 주소록 API - 공동구매 참여(participateInGroupBuy)에 넘길 buyerAddressId를 여기서 얻는다
export function listMyAddresses(): Promise<BuyerAddressResponse[]> {
  return http.get<BuyerAddressResponse[]>("/api/members/me/addresses", { auth: true });
}

export function createMyAddress(request: BuyerAddressCreateRequest): Promise<BuyerAddressResponse> {
  return http.post<BuyerAddressResponse>("/api/members/me/addresses", request, { auth: true });
}

export function deleteMyAddress(addressId: number): Promise<void> {
  return http.delete<void>(`/api/members/me/addresses/${addressId}`, { auth: true });
}

// 기존 기본 배송지는 자동으로 해제되고, 지정한 배송지가 새 기본 배송지가 된다
export function setDefaultAddress(addressId: number): Promise<void> {
  return http.patch<void>(`/api/members/me/addresses/${addressId}/default`, undefined, { auth: true });
}
