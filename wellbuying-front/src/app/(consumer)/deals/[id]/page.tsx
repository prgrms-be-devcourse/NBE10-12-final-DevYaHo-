"use client";

import { useState } from "react";
import { notFound, useParams } from "next/navigation";
import { Heart, PieChart } from "lucide-react";
import { DealArtwork } from "@/components/deal/DealArtwork";
import { ParticipationModal } from "@/components/consumer/ParticipationModal";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { Tag } from "@/components/ui/Tag";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { DEAL_STATUS_LABEL, activeTier, nextTier, tierProfit, tierMargin, won } from "@/lib/mock/types";

export default function DealDetailPage() {
  const params = useParams<{ id: string }>();
  const { dealById, participants, favoriteIds, toggleFavorite, dealStatuses, hasActiveParticipation } =
    useDemoStore();
  const [showParticipation, setShowParticipation] = useState(false);
  const [showSuccess, setShowSuccess] = useState(false);

  const deal = dealById(params.id);
  if (!deal) {
    notFound();
  }

  const people = participants(deal.id);
  const tier = activeTier(deal, people);
  const status = dealStatuses[deal.id] ?? "recruiting";
  const participated = hasActiveParticipation(deal.id);
  const isFavorite = favoriteIds.has(deal.id);

  const buttonLabel = participated
    ? "이미 참여한 공동구매예요"
    : status === "recruiting"
      ? "공동구매 참여하기"
      : DEAL_STATUS_LABEL[status];

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-9">
      <div className="grid gap-7 rounded-2xl border border-wb-line bg-wb-surface p-6 md:grid-cols-2">
        <DealArtwork deal={deal} className="h-64 w-full md:h-full" />
        <div className="flex flex-col">
          <div className="flex items-center justify-between">
            <div className="flex gap-2">
              <Tag>{deal.category}</Tag>
              <Tag highlighted>마감 D-{deal.daysLeft}</Tag>
            </div>
            <button
              onClick={() => toggleFavorite(deal.id)}
              className="flex h-10 w-10 items-center justify-center rounded-full border border-wb-line"
            >
              <Heart className={`h-4 w-4 ${isFavorite ? "fill-wb-orange text-wb-orange" : "text-wb-ink"}`} />
            </button>
          </div>
          <h1 className="mt-4 text-2xl font-bold">{deal.title}</h1>
          <p className="mt-2 text-sm font-semibold text-wb-green">{deal.producer}</p>
          <p className="mt-3 text-sm leading-relaxed text-wb-secondary">{deal.summary}</p>

          <div className="mt-auto space-y-3 pt-6">
            <p className="text-xs font-bold text-wb-secondary">현재 {people.toLocaleString("ko-KR")}명 가격</p>
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-bold">{won(tier.price)}</span>
              <span className="text-sm text-wb-secondary">/ 개</span>
            </div>
            <Button
              className="w-full"
              disabled={participated || status !== "recruiting"}
              onClick={() => setShowParticipation(true)}
            >
              {buttonLabel}
            </Button>
          </div>
        </div>
      </div>

      {showSuccess && (
        <Banner tone="success">
          공동구매 참여가 완료됐어요. 왼쪽 &lsquo;참여 내역&rsquo;에서 진행 상태를 확인할 수 있어요.
        </Banner>
      )}

      <PriceJourneyCard dealId={deal.id} />

      <div className="grid gap-5 md:grid-cols-2">
        <TransparencyCard tier={tier} />
        <ProducerStoryCard deal={deal} />
      </div>

      <ParticipationModal
        deal={deal}
        open={showParticipation}
        onClose={() => {
          setShowParticipation(false);
          setShowSuccess(true);
        }}
      />
    </div>
  );
}

function PriceJourneyCard({ dealId }: { dealId: string }) {
  const { dealById, participants } = useDemoStore();
  const deal = dealById(dealId)!;
  const people = participants(dealId);
  const next = nextTier(deal, people);
  const current = activeTier(deal, people);

  return (
    <div className="space-y-5 rounded-2xl border border-wb-line bg-wb-surface p-6">
      <div className="flex items-start justify-between">
        <div>
          <h2 className="text-lg font-bold">함께할수록 내려가는 가격</h2>
          <p className="mt-1 text-sm text-wb-green">
            {next
              ? `${(next.minimumPeople - people).toLocaleString("ko-KR")}명만 더 모이면 ${won(next.price)}`
              : "가장 낮은 가격 구간에 도달했어요"}
          </p>
        </div>
        <p className="text-sm font-bold">현재 {people.toLocaleString("ko-KR")}명</p>
      </div>
      <ProgressBar value={people / deal.targetPeople} />
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        {deal.tiers.map((t) => {
          const active = current.minimumPeople === t.minimumPeople;
          return (
            <div
              key={t.minimumPeople}
              className={`rounded-xl p-4 ${active ? "bg-wb-light-green/60" : "bg-wb-canvas"}`}
            >
              <p className={`text-xs font-semibold ${active ? "text-wb-green" : "text-wb-secondary"}`}>
                {t.minimumPeople.toLocaleString("ko-KR")}명부터
              </p>
              <p className="mt-1.5 text-lg font-bold">{won(t.price)}</p>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function TransparencyCard({ tier }: { tier: ReturnType<typeof activeTier> }) {
  return (
    <div className="space-y-4 rounded-2xl border border-wb-line bg-wb-surface p-6">
      <div className="flex items-start justify-between">
        <div>
          <h2 className="text-base font-bold">이 가격은 이렇게 만들어져요</h2>
          <p className="text-xs text-wb-secondary">현재 가격 구간 기준 · 1개</p>
        </div>
        <PieChart className="h-6 w-6 text-wb-green/70" />
      </div>
      <div className="flex items-center justify-between text-sm">
        <span className="flex items-center gap-2.5">
          <span className="h-6 w-2 rounded bg-wb-light-green" /> 생산·포장·유통
        </span>
        <span className="font-bold">{won(tier.cost)}</span>
      </div>
      <div className="flex items-center justify-between text-sm">
        <span className="flex items-center gap-2.5">
          <span className="h-6 w-2 rounded bg-wb-orange/70" /> 생산자 이익
        </span>
        <span className="font-bold">{won(tierProfit(tier))}</span>
      </div>
      <div className="flex items-center justify-between border-t border-wb-line pt-3 text-sm font-bold">
        <span>판매 가격</span>
        <span>{won(tier.price)}</span>
      </div>
      <p className="rounded-lg bg-wb-canvas p-3 text-xs text-wb-secondary">
        생산자 이익률 {tierMargin(tier)}% · 수량이 늘어도 생산자의 개당 이익은 지켜요.
      </p>
    </div>
  );
}

function ProducerStoryCard({ deal }: { deal: ReturnType<typeof useDemoStore>["deals"][number] }) {
  return (
    <div className="space-y-4 rounded-2xl border border-wb-line bg-wb-surface p-6">
      <div className="flex items-center gap-3">
        <div className="flex h-11 w-11 items-center justify-center rounded-full bg-wb-light-green">
          <span className="text-sm font-bold text-wb-green">{deal.producer.slice(0, 1)}</span>
        </div>
        <div>
          <p className="text-sm font-bold">{deal.producer}</p>
          <p className="text-xs font-semibold text-wb-green">생산 정보 인증</p>
        </div>
      </div>
      <h3 className="text-base font-bold">왜 이 상품을 만들었나요?</h3>
      <p className="text-sm leading-relaxed text-wb-secondary">{deal.detail}</p>
      <p className="text-xs font-semibold text-wb-green">원산지·제조 공정 상세 정보 공개</p>
    </div>
  );
}
