"use client";

import Link from "next/link";
import { ArrowDownCircle, ArrowRight, Quote } from "lucide-react";
import { DealArtwork } from "@/components/deal/DealArtwork";
import { DealCard } from "@/components/deal/DealCard";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { Tag } from "@/components/ui/Tag";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { activeTier, nextTier, won, type Deal } from "@/lib/mock/types";

function featuredPriority(deal: Deal, people: number) {
  const next = nextTier(deal, people);
  if (!next) return null;
  return (next.minimumPeople - people) / next.minimumPeople;
}

export default function HomePage() {
  const { visibleDeals, participants } = useDemoStore();
  const deals = visibleDeals();

  const withPriority = deals
    .map((deal) => ({ deal, priority: featuredPriority(deal, participants(deal.id)) }))
    .filter((item) => item.priority !== null)
    .sort((a, b) => (a.priority as number) - (b.priority as number));

  const featured = withPriority[0]?.deal ?? null;
  const remaining = deals.filter((deal) => deal.id !== featured?.id);
  const featuredPeople = featured ? participants(featured.id) : 0;
  const featuredNext = featured ? nextTier(featured, featuredPeople) : null;

  return (
    <div className="mx-auto max-w-5xl space-y-8 px-6 py-9">
      <div>
        <p className="text-sm font-semibold text-wb-green">좋은 아침이에요</p>
        <h1 className="mt-1 text-3xl font-bold">가격을 알면, 구매가 달라져요</h1>
      </div>

      {featured && featuredNext && (
        <section className="space-y-3">
          <div className="flex items-center gap-2 text-wb-green">
            <ArrowDownCircle className="h-5 w-5" />
            <h2 className="text-lg font-bold">다음 가격 인하가 가장 가까워요</h2>
          </div>
          <p className="text-sm text-wb-secondary">
            {(featuredNext.minimumPeople - featuredPeople).toLocaleString("ko-KR")}명 더 모이면{" "}
            {won(featuredNext.price)}으로 내려가요.
          </p>
          <FeaturedDealCard deal={featured} />
        </section>
      )}

      {remaining.length > 0 && (
        <section className="space-y-3">
          <div>
            <h2 className="text-lg font-bold">{featured ? "다른 진행 중 공동구매" : "지금 진행 중인 공동구매"}</h2>
            <p className="text-sm text-wb-secondary">생산 과정과 가격을 투명하게 공개했어요</p>
          </div>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {remaining.map((deal) => (
              <DealCard key={deal.id} deal={deal} />
            ))}
          </div>
        </section>
      )}

      <section className="flex items-center gap-5 rounded-2xl bg-wb-green/[0.07] p-6">
        <Quote className="h-8 w-8 shrink-0 text-wb-green" strokeWidth={1.5} />
        <div>
          <p className="font-bold">싼 가격보다 납득할 수 있는 가격</p>
          <p className="mt-1 text-sm text-wb-secondary">
            WellBuying은 생산비와 이익을 공개한 생산자의 상품을 소개합니다.
          </p>
        </div>
      </section>
    </div>
  );
}

function FeaturedDealCard({ deal }: { deal: Deal }) {
  const { participants } = useDemoStore();
  const people = participants(deal.id);
  const tier = activeTier(deal, people);
  const next = nextTier(deal, people);

  return (
    <Link
      href={`/deals/${deal.id}`}
      className="grid gap-6 rounded-2xl border border-wb-line bg-wb-surface p-6 transition-shadow hover:shadow-md md:grid-cols-2"
    >
      <DealArtwork deal={deal} className="h-52 w-full md:h-full" />
      <div className="flex flex-col">
        <div className="flex gap-2">
          <Tag highlighted>마감 D-{deal.daysLeft}</Tag>
          {next && <Tag>가격 인하까지 {(next.minimumPeople - people).toLocaleString("ko-KR")}명</Tag>}
        </div>
        <h3 className="mt-4 text-2xl font-bold">{deal.title}</h3>
        <p className="mt-1.5 text-sm font-medium text-wb-secondary">{deal.producer}</p>
        <div className="mt-auto space-y-3 pt-4">
          <div className="flex items-baseline gap-2">
            <span className="text-2xl font-bold">{won(tier.price)}</span>
            <span className="text-xs font-semibold text-wb-secondary">현재 가격</span>
          </div>
          <ProgressBar value={people / deal.targetPeople} />
          <div className="flex justify-between text-xs">
            <span className="font-bold text-wb-green">{people.toLocaleString("ko-KR")}명 참여</span>
            <span className="text-wb-secondary">목표 {deal.targetPeople.toLocaleString("ko-KR")}명</span>
          </div>
          <div className="flex items-center justify-end gap-1.5 text-xs font-bold text-wb-green">
            공동구매 자세히 보기
            <ArrowRight className="h-3.5 w-3.5" />
          </div>
        </div>
      </div>
    </Link>
  );
}
