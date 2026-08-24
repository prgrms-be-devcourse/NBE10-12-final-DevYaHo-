"use client";

import { useState } from "react";
import { AlertTriangle, Search } from "lucide-react";
import { DealArtwork } from "@/components/deal/DealArtwork";
import { ProducerStatusPill } from "@/components/producer/ProducerStatusPill";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { StatusPill } from "@/components/ui/Tag";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { activeTier, won, type Deal } from "@/lib/mock/types";

export default function AdminDealsPage() {
  const { deals, dealStatuses, participants } = useDemoStore();
  const [query, setQuery] = useState("");
  const [selectedDeal, setSelectedDeal] = useState<Deal | null>(null);

  const filtered = deals.filter(
    (deal) =>
      query.trim().length === 0 ||
      deal.title.toLowerCase().includes(query.toLowerCase()) ||
      deal.producer.toLowerCase().includes(query.toLowerCase()),
  );

  const recruitingCount = deals.filter((deal) => dealStatuses[deal.id] === "recruiting").length;
  const pausedCount = deals.filter((deal) => dealStatuses[deal.id] === "paused").length;

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-6 py-9">
      <div>
        <p className="text-xs font-bold tracking-wide text-wb-green">GROUP BUYING</p>
        <h1 className="mt-1 text-3xl font-bold">공동구매 관리</h1>
        <p className="mt-1 text-sm text-wb-secondary">참여자 수, 가격 구간, 남은 시간을 실시간으로 확인합니다.</p>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex h-11 flex-1 items-center gap-2.5 rounded-xl border border-wb-line bg-wb-surface px-3.5 sm:max-w-xs">
          <Search className="h-4 w-4 text-wb-secondary" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="공동구매 또는 생산자 검색"
            className="w-full bg-transparent text-sm outline-none"
          />
        </div>
        <div className="flex gap-2">
          <StatusPill tone="green">진행 {recruitingCount}</StatusPill>
          <StatusPill tone="orange">일시중지 {pausedCount}</StatusPill>
        </div>
      </div>

      {/* wide table */}
      <div className="hidden overflow-hidden rounded-2xl border border-wb-line bg-wb-surface md:block">
        <div className="grid grid-cols-[2fr_1fr_100px_60px_110px_70px] gap-3 border-b border-wb-line bg-wb-canvas/60 px-5 py-2.5 text-xs font-bold text-wb-secondary">
          <span>공동구매</span>
          <span>실시간 참여</span>
          <span className="text-right">현재 가격</span>
          <span>마감</span>
          <span>상태</span>
          <span />
        </div>
        {filtered.map((deal) => {
          const people = participants(deal.id);
          const status = dealStatuses[deal.id] ?? "draft";
          return (
            <div
              key={deal.id}
              className="grid grid-cols-[2fr_1fr_100px_60px_110px_70px] items-center gap-3 border-b border-wb-line px-5 py-3.5 last:border-0"
            >
              <div className="flex min-w-0 items-center gap-3">
                <DealArtwork deal={deal} className="h-11 w-12 shrink-0" />
                <div className="min-w-0">
                  <p className="line-clamp-1 text-sm font-bold">{deal.title}</p>
                  <p className="line-clamp-1 text-xs text-wb-secondary">{deal.producer}</p>
                </div>
              </div>
              <div className="space-y-1">
                <p className="text-xs font-bold">
                  {people.toLocaleString("ko-KR")} / {deal.targetPeople.toLocaleString("ko-KR")}명
                </p>
                <ProgressBar value={people / deal.targetPeople} />
              </div>
              <p className="text-right text-xs font-bold">{won(activeTier(deal, people).price)}</p>
              <p className="text-xs font-semibold">D-{deal.daysLeft}</p>
              <ProducerStatusPill status={status} />
              <Button variant="secondary" onClick={() => setSelectedDeal(deal)} className="!h-8 !px-3 text-xs">
                관리
              </Button>
            </div>
          );
        })}
      </div>

      {/* compact cards */}
      <div className="space-y-3 md:hidden">
        {filtered.map((deal) => {
          const people = participants(deal.id);
          const status = dealStatuses[deal.id] ?? "draft";
          return (
            <div key={deal.id} className="space-y-3 rounded-xl border border-wb-line bg-wb-surface p-4">
              <div className="flex items-start gap-3">
                <DealArtwork deal={deal} className="h-12 w-14 shrink-0" />
                <div className="min-w-0 flex-1">
                  <p className="line-clamp-1 text-sm font-bold">{deal.title}</p>
                  <p className="line-clamp-1 text-xs text-wb-secondary">{deal.producer}</p>
                </div>
                <div className="flex flex-col items-end gap-1.5">
                  <ProducerStatusPill status={status} />
                  <Button variant="secondary" onClick={() => setSelectedDeal(deal)} className="!h-7 !px-2.5 text-xs">
                    관리
                  </Button>
                </div>
              </div>
              <div className="flex items-center gap-2.5 text-xs">
                <span className="font-bold">{people.toLocaleString("ko-KR")}명</span>
                <ProgressBar value={people / deal.targetPeople} />
                <span className="font-bold">{won(activeTier(deal, people).price)}</span>
                <span className="font-semibold">D-{deal.daysLeft}</span>
              </div>
            </div>
          );
        })}
      </div>

      <ManageDealModal deal={selectedDeal} onClose={() => setSelectedDeal(null)} />
    </div>
  );
}

function ManageDealModal({ deal, onClose }: { deal: Deal | null; onClose: () => void }) {
  const { dealStatuses, participants, setDealStatus } = useDemoStore();
  if (!deal) return null;

  const people = participants(deal.id);
  const status = dealStatuses[deal.id] ?? "draft";
  const paused = status === "paused";

  return (
    <Modal open={deal !== null} onClose={onClose} title="공동구매 운영 상세" subtitle={deal.title} width="560px">
      <div className="space-y-5">
        <div className="grid grid-cols-3 gap-2.5">
          <div className="rounded-lg bg-wb-canvas p-3">
            <p className="text-xs text-wb-secondary">참여자</p>
            <p className="text-lg font-bold">{people.toLocaleString("ko-KR")}명</p>
          </div>
          <div className="rounded-lg bg-wb-canvas p-3">
            <p className="text-xs text-wb-secondary">목표 달성</p>
            <p className="text-lg font-bold">{Math.round((people / deal.targetPeople) * 100)}%</p>
          </div>
          <div className="rounded-lg bg-wb-canvas p-3">
            <p className="text-xs text-wb-secondary">남은 기간</p>
            <p className="text-lg font-bold">D-{deal.daysLeft}</p>
          </div>
        </div>

        <div>
          <p className="mb-2.5 text-sm font-bold">가격 구간</p>
          <div className="space-y-2">
            {deal.tiers.map((tier) => {
              const active = activeTier(deal, people).minimumPeople === tier.minimumPeople;
              return (
                <div
                  key={tier.minimumPeople}
                  className={`flex items-center justify-between rounded-lg p-3 text-sm ${
                    active ? "bg-wb-light-green/45" : "bg-wb-canvas"
                  }`}
                >
                  <span className="font-semibold">{tier.minimumPeople.toLocaleString("ko-KR")}명부터</span>
                  <span className="font-bold">{won(tier.price)}</span>
                </div>
              );
            })}
          </div>
        </div>

        <div className="flex items-start gap-2.5 rounded-xl bg-wb-orange/10 p-3.5 text-xs text-wb-secondary">
          <AlertTriangle className="h-4 w-4 shrink-0 text-wb-orange" />
          일시중지하면 신규 참여만 막히며 기존 참여 내역은 유지됩니다.
        </div>

        <div className="flex items-center justify-between border-t border-wb-line pt-4">
          <ProducerStatusPill status={status} />
          <Button
            disabled={status !== "recruiting" && status !== "paused"}
            onClick={() => setDealStatus(deal.id, paused ? "recruiting" : "paused")}
          >
            {paused ? "공동구매 재개" : "공동구매 일시중지"}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
