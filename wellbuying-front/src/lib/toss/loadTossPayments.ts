// 토스 결제 SDK를 필요한 시점에만 로드한다.
// npm 패키지 대신 스크립트 태그를 쓰는 이유는, 이 페이지가 결제 모듈 검증용 임시 화면이라
// 정식 카드 등록 플로우(06-billingkey-registration-ui.md)를 만들 때 통째로 교체될 예정이기 때문이다.

const SDK_URL = "https://js.tosspayments.com/v1/payment";

type BillingAuthOptions = {
  customerKey: string;
  successUrl: string;
  failUrl: string;
};

type TossPaymentsInstance = {
  requestBillingAuth: (method: string, options: BillingAuthOptions) => Promise<void>;
};

declare global {
  interface Window {
    TossPayments?: (clientKey: string) => TossPaymentsInstance;
  }
}

let loading: Promise<void> | null = null;

function loadScript(): Promise<void> {
  if (window.TossPayments) return Promise.resolve();
  if (loading) return loading;

  loading = new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = SDK_URL;
    script.onload = () => resolve();
    script.onerror = () => {
      loading = null;
      reject(new Error("토스 결제 SDK를 불러오지 못했습니다."));
    };
    document.head.appendChild(script);
  });
  return loading;
}

export async function loadTossPayments(clientKey: string): Promise<TossPaymentsInstance> {
  await loadScript();
  if (!window.TossPayments) {
    throw new Error("토스 결제 SDK가 초기화되지 않았습니다.");
  }
  return window.TossPayments(clientKey);
}
