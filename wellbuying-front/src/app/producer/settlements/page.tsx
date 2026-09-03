"use client";

import { StatusPill } from "@/components/ui/Tag";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { SETTLEMENT_STATUS_LABEL, won } from "@/lib/mock/types";

export default function ProducerSettlementsPage() {
  const { settlements, settlementStatuses } = useDemoStore();
  const records = settlements.filter((record) => record.producer === "푸른살림 연구소");

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div>
        <h1 className="text-3xl font-bold">정산 내역</h1>
        <p className="mt-1 text-sm text-wb-secondary">공동구매별 매출과 수수료, 지급 예정액을 확인하세요.</p>
      </div>

      <div className="space-y-4">
        {records.map((record) => {
          const status = settlementStatuses[record.id] ?? "ready";
          return (
            <div key={record.id} className="space-y-4 rounded-2xl border border-wb-line bg-wb-surface p-5">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-lg font-bold">{record.groupBuyTitle}</p>
                  <p className="text-xs text-wb-secondary">정산번호 ST-202608-{record.id.slice(-4)}</p>
                </div>
                <StatusPill tone={status === "completed" ? "green" : "orange"}>
                  {SETTLEMENT_STATUS_LABEL[status]}
                </StatusPill>
              </div>
              <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-3">
                <SettlementValue title="총 매출" value={record.sales} />
                <SettlementValue title="플랫폼 수수료" value={record.platformFee} />
                <SettlementValue title="지급 예정" value={record.payout} highlighted />
              </div>
              <p className="text-xs text-wb-secondary">정산 계좌 · 국민은행 123-45-****** · 영업일 기준 3일 이내 지급</p>
            </div>
          );
        })}
        {records.length === 0 && (
          <p className="text-sm text-wb-secondary">아직 정산 내역이 없어요.</p>
        )}
      </div>
    </div>
  );
}

function SettlementValue({
  title,
  value,
  highlighted = false,
}: {
  title: string;
  value: number;
  highlighted?: boolean;
}) {
  return (
    <div className={`rounded-lg p-3.5 ${highlighted ? "bg-wb-light-green/50" : "bg-wb-canvas"}`}>
      <p className="text-xs text-wb-secondary">{title}</p>
      <p className={`text-base font-bold ${highlighted ? "text-wb-green" : ""}`}>{won(value)}</p>
    </div>
  );
}
