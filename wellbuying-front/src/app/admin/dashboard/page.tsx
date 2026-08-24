"use client";

import Link from "next/link";
import { Banknote, ChevronRight, Package, Users2, Wallet } from "lucide-react";
import { DealArtwork } from "@/components/deal/DealArtwork";
import { MetricCard } from "@/components/ui/MetricCard";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { won } from "@/lib/mock/types";

const SERVICES = [
  { name: "Spring Boot", latency: "18ms" },
  { name: "PostgreSQL", latency: "7ms" },
  { name: "Redis", latency: "2ms" },
  { name: "Kafka", latency: "0 lag" },
];

export default function AdminDashboardPage() {
  const { deals, dealStatuses, participants, pendingReviewCount, readySettlementCount, readySettlementAmount } =
    useDemoStore();

  const activeDeals = deals.filter((deal) => dealStatuses[deal.id] === "recruiting");
  const totalParticipants = deals.reduce((sum, deal) => sum + participants(deal.id), 0);

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-6 py-9">
      <div>
        <p className="text-xs font-bold tracking-wide text-wb-green">OVERVIEW</p>
        <h1 className="mt-1 text-3xl font-bold">운영 대시보드</h1>
        <p className="mt-1 text-sm text-wb-secondary">실시간 운영 현황</p>
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <MetricCard icon={Users2} title="진행 공동구매" value={`${activeDeals.length}건`} detail="모집 중" />
        <MetricCard icon={Package} title="현재 참여자" value={`${totalParticipants.toLocaleString("ko-KR")}명`} />
        <MetricCard icon={Banknote} title="심사 대기" value={`${pendingReviewCount}건`} detail="확인 필요" />
        <MetricCard icon={Wallet} title="정산 대기" value={won(readySettlementAmount)} detail={`${readySettlementCount}건`} />
      </div>

      <div className="grid gap-5 lg:grid-cols-[1.4fr_1fr]">
        <div className="space-y-4 rounded-2xl border border-wb-line bg-wb-surface p-6">
          <div>
            <h2 className="text-lg font-bold">확인이 필요한 작업</h2>
            <p className="text-xs text-wb-secondary">우선순위가 높은 운영 업무예요.</p>
          </div>
          <Link
            href="/admin/reviews"
            className="flex items-center gap-3.5 rounded-xl bg-wb-canvas p-3.5 hover:bg-wb-light-green/30"
          >
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-wb-green/12 text-wb-green">
              <Package className="h-4 w-4" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-bold">신규 상품 심사</p>
              <p className="text-xs text-wb-secondary">가격 구조와 생산 정보를 확인해주세요.</p>
            </div>
            <span className="text-sm font-bold text-wb-green">{pendingReviewCount}건</span>
            <ChevronRight className="h-4 w-4 text-wb-secondary" />
          </Link>
          <Link
            href="/admin/settlements"
            className="flex items-center gap-3.5 rounded-xl bg-wb-canvas p-3.5 hover:bg-wb-light-green/30"
          >
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-wb-orange/12 text-wb-orange">
              <Banknote className="h-4 w-4" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-bold">정산 배치 대기</p>
              <p className="text-xs text-wb-secondary">검증 완료된 정산 건을 실행할 수 있어요.</p>
            </div>
            <span className="text-sm font-bold text-wb-orange">{readySettlementCount}건</span>
            <ChevronRight className="h-4 w-4 text-wb-secondary" />
          </Link>
        </div>

        <div className="space-y-4 rounded-2xl border border-wb-line bg-wb-surface p-6">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-bold">워크로드 상태</h2>
            <span className="text-[10px] font-bold text-green-600">LIVE</span>
          </div>
          {SERVICES.map((service) => (
            <div key={service.name} className="flex items-center gap-2.5 text-sm">
              <span className="h-1.5 w-1.5 rounded-full bg-green-500" />
              <span className="font-semibold">{service.name}</span>
              <span className="ml-auto text-xs text-wb-secondary">{service.latency}</span>
              <span className="text-xs font-bold text-green-600">정상</span>
            </div>
          ))}
        </div>
      </div>

      <div className="space-y-4 rounded-2xl border border-wb-line bg-wb-surface p-6">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold">진행 중 공동구매</h2>
          <Link href="/admin/deals" className="text-xs font-bold text-wb-green">
            전체 보기
          </Link>
        </div>
        <div className="divide-y divide-wb-line">
          {activeDeals.map((deal) => {
            const people = participants(deal.id);
            return (
              <div key={deal.id} className="flex items-center gap-4 py-3.5 first:pt-0 last:pb-0">
                <DealArtwork deal={deal} className="h-14 w-16 shrink-0" />
                <div className="min-w-0 flex-1">
                  <p className="line-clamp-1 text-sm font-bold">{deal.title}</p>
                  <p className="text-xs text-wb-secondary">{deal.producer}</p>
                </div>
                <div className="hidden w-32 sm:block">
                  <ProgressBar value={people / deal.targetPeople} />
                </div>
                <p className="w-20 shrink-0 text-right text-sm font-bold">{people.toLocaleString("ko-KR")}명</p>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
