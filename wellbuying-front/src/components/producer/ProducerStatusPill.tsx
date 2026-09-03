import { StatusPill } from "@/components/ui/Tag";
import { DEAL_STATUS_LABEL, type DealStatus } from "@/lib/mock/types";

const TONE: Record<DealStatus, "green" | "orange" | "red" | "blue" | "neutral"> = {
  recruiting: "green",
  completed: "blue",
  paused: "orange",
  scheduled: "orange",
  cancelled: "red",
  failed: "red",
  draft: "neutral",
};

export function ProducerStatusPill({ status }: { status: DealStatus }) {
  return <StatusPill tone={TONE[status]}>{DEAL_STATUS_LABEL[status]}</StatusPill>;
}
