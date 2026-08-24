import type { LucideIcon } from "lucide-react";
import { Card } from "@/components/ui/Card";

export function MetricCard({
  icon: Icon,
  title,
  value,
  detail,
}: {
  icon: LucideIcon;
  title: string;
  value: string;
  detail?: string;
}) {
  return (
    <Card className="flex items-center gap-3.5">
      <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-wb-light-green/60 text-wb-green">
        <Icon className="h-5 w-5" strokeWidth={2} />
      </div>
      <div className="min-w-0">
        <p className="text-xs font-semibold text-wb-secondary">{title}</p>
        <p className="text-xl font-bold">{value}</p>
        {detail && <p className="text-xs font-semibold text-wb-green">{detail}</p>}
      </div>
    </Card>
  );
}
