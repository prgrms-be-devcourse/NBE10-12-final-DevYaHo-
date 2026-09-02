// 카드 등록은 토스 결제창으로 페이지를 완전히 떠났다가 돌아오는 흐름이라, 그 사이 화면의 폼 상태가
// 전부 사라진다. 되돌아왔을 때 사용자가 수량·배송지를 다시 입력하지 않아도 되도록 잠깐 보관해 둔다.
// sessionStorage를 쓰는 이유는 탭 단위로 격리되고 탭을 닫으면 사라져서, 결제 정보 입력 도중 이탈한
// 흔적이 다음 방문까지 남지 않기 때문이다.
const KEY = "wb.pendingParticipation";

export type PendingParticipation = {
  groupBuyId: number;
  quantity: number;
  address: string;
  addressDetail: string;
  zipcode: string;
};

export function savePendingParticipation(pending: PendingParticipation): void {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.setItem(KEY, JSON.stringify(pending));
  } catch {
    // 저장에 실패해도 등록 자체는 진행시킨다 - 돌아왔을 때 폼을 다시 채워야 할 뿐이다
  }
}

// 한 번 읽으면 지운다. 카드 등록을 도중에 그만둔 경우 남은 값이 다음 방문에서 결제 창을 다시
// 띄우는 걸 막기 위해, 복원은 항상 1회성으로 취급한다.
export function takePendingParticipation(): PendingParticipation | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.sessionStorage.getItem(KEY);
    window.sessionStorage.removeItem(KEY);
    return raw ? (JSON.parse(raw) as PendingParticipation) : null;
  } catch {
    return null;
  }
}

export function clearPendingParticipation(): void {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.removeItem(KEY);
  } catch {
    // 무시 - 어차피 1회성 값이다
  }
}
