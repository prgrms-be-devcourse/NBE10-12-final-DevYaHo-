export function won(value: number): string {
  return `${value.toLocaleString("ko-KR")}원`;
}

// 요약 카드처럼 좁은 영역에 숫자를 표시할 때 줄바꿈을 막기 위한 축약 표기
// (예: 3,630 -> "3.63천", 36,000 -> "3.6만"). 상세 화면에서는 사용하지 말고 전체 숫자를 그대로 보여줄 것.
export function compactNumber(value: number): string {
  const sign = value < 0 ? "-" : "";
  const abs = Math.abs(value);
  if (abs >= 10000) {
    return `${sign}${(abs / 10000).toFixed(1)}만`;
  }
  if (abs >= 1000) {
    return `${sign}${(abs / 1000).toFixed(2)}천`;
  }
  return `${sign}${abs.toLocaleString("ko-KR")}`;
}

export function compactCount(value: number, unit: string): string {
  return `${compactNumber(value)}${unit}`;
}

export function compactWon(value: number): string {
  return `${compactNumber(value)}원`;
}

export function formatDateTime(value: string): string {
  return new Date(value).toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function formatRemaining(seconds: number): string {
  if (seconds <= 0) return "마감";
  const days = Math.floor(seconds / 86_400);
  const hours = Math.floor((seconds % 86_400) / 3_600);
  const minutes = Math.floor((seconds % 3_600) / 60);
  if (days > 0) return `${days}일 ${hours}시간 남음`;
  if (hours > 0) return `${hours}시간 ${minutes}분 남음`;
  return `${minutes}분 남음`;
}
