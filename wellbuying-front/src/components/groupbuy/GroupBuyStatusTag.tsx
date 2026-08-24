import { StatusPill } from "@/components/ui/Tag";
import type { GroupBuyStatus } from "@/lib/api/types";

const LABEL: Record<GroupBuyStatus, string> = {
  READY: "오픈 예정",
  ONGOING: "모집 중",
  SUCCESS: "성사",
  FAILED: "목표 미달",
  CANCELED: "취소",
};

const TONE: Record<GroupBuyStatus, "green" | "orange" | "red" | "blue" | "neutral"> = {
  READY: "blue",
  ONGOING: "green",
  SUCCESS: "green",
  FAILED: "red",
  CANCELED: "neutral",
};

export function GroupBuyStatusTag({ status }: { status: GroupBuyStatus }) {
  return <StatusPill tone={TONE[status]}>{LABEL[status]}</StatusPill>;
}
