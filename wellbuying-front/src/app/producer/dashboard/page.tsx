"use client";

import { useState } from "react";
import { LineChart, Plus, Radio, Users } from "lucide-react";
import { DealArtwork } from "@/components/deal/DealArtwork";
import { DealEditorModal } from "@/components/producer/DealEditorModal";
import { ProducerStatusPill } from "@/components/producer/ProducerStatusPill";
import { Button } from "@/components/ui/Button";
import { MetricCard } from "@/components/ui/MetricCard";
import { compactCount, compactWon } from "@/lib/format";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { activeTier, won } from "@/lib/mock/types";

export default function ProducerDashboardPage() {
  const { deals, producerDealIds, participants, dealStatuses } = useDemoStore();
  const [showCreate, setShowCreate] = useState(false);

  const producerDeals = deals.filter((deal) => producerDealIds.has(deal.id));
  const recruitingCount = producerDeals.filter((deal) => dealStatuses[deal.id] === "recruiting").length;
  const totalParticipants = producerDeals.reduce((sum, deal) => sum + participants(deal.id), 0);
  const estimatedSales = producerDeals.reduce((sum, deal) => {
    const people = participants(deal.id);
    return sum + people * activeTier(deal, people).price;
  }, 0);

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">생산자 홈</h1>
          <p className="mt-1 text-sm text-wb-secondary">가격을 투명하게 설계하고 공동구매를 운영하세요.</p>
        </div>
        <Button onClick={() => setShowCreate(true)}>
          <Plus className="h-4 w-4" /> 새 공동구매
        </Button>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <MetricCard icon={Radio} title="운영 중" value={`${recruitingCount}건`} />
        <MetricCard icon={Users} title="전체 참여" value={compactCount(totalParticipants, "명")} />
        <MetricCard icon={LineChart} title="예상 매출" value={compactWon(estimatedSales)} />
      </div>

      <div className="rounded-2xl border border-wb-line bg-wb-surface p-6">
        <h2 className="text-lg font-bold">내 공동구매 현황</h2>
        {producerDeals.length === 0 ? (
          <p className="mt-4 text-sm text-wb-secondary">아직 개설한 공동구매가 없어요.</p>
        ) : (
          <div className="mt-4 divide-y divide-wb-line">
            {producerDeals.slice(0, 3).map((deal) => {
              const people = participants(deal.id);
              return (
                <div key={deal.id} className="flex items-center gap-4 py-3.5 first:pt-0 last:pb-0">
                  <DealArtwork deal={deal} className="h-16 w-20 shrink-0" />
                  <div className="min-w-0 flex-1">
                    <p className="line-clamp-1 text-sm font-bold">{deal.title}</p>
                    <p className="text-xs text-wb-secondary">
                      {people.toLocaleString("ko-KR")}명 · {won(activeTier(deal, people).price)}
                    </p>
                  </div>
                  <ProducerStatusPill status={dealStatuses[deal.id] ?? "draft"} />
                </div>
              );
            })}
          </div>
        )}
      </div>

      <DealEditorModal open={showCreate} onClose={() => setShowCreate(false)} />
    </div>
  );
}
