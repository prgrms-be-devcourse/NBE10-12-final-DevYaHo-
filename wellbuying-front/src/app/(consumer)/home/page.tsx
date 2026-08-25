"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ArrowDownCircle,
  ArrowRight,
  CalendarClock,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Flame,
  LayoutGrid,
  PackageSearch,
  Quote,
  Rocket,
  Search,
  Sparkles,
} from "lucide-react";
import { DealArtwork } from "@/components/deal/DealArtwork";
import { DealCard } from "@/components/deal/DealCard";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { Tag } from "@/components/ui/Tag";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { activeTier, nextTier, won, type Deal } from "@/lib/mock/types";

const CATEGORIES = ["전체", "식품", "생활", "패션"];
const CAROUSEL_INTERVAL_MS = 4500;
const PROMO_COUNT = 3;
const POPULAR_COUNT = 4;
const NOTABLE_COUNT = 8;
const CLOSING_SOON_COUNT = 8;

const SUB_NAV = [
  { label: "진행중인 공동구매", href: "/explore" },
  { label: "진행예정 공동구매", href: "/explore?status=scheduled" },
  { label: "인기 공동구매", href: "/ranking" },
  { label: "신규 공동구매", href: "/explore?sort=new" },
  { label: "마감임박", href: "/explore?sort=closing" },
];

function featuredPriority(deal: Deal, people: number) {
  const next = nextTier(deal, people);
  if (!next) return null;
  return (next.minimumPeople - people) / next.minimumPeople;
}

const UPCOMING_COUNT = 4;
const NEW_ARRIVAL_COUNT = 4;

export default function HomePage() {
  const { visibleDeals, scheduledDeals, participants } = useDemoStore();
  const router = useRouter();
  const [category, setCategory] = useState("전체");
  const [categoryMenuOpen, setCategoryMenuOpen] = useState(false);
  const [searchValue, setSearchValue] = useState("");
  const [slide, setSlide] = useState(0);

  const deals = visibleDeals();
  const filtered = category === "전체" ? deals : deals.filter((deal) => deal.category === category);

  const promoDeals = useMemo(() => {
    const withPriority = filtered
      .map((deal) => ({ deal, priority: featuredPriority(deal, participants(deal.id)) }))
      .filter((item) => item.priority !== null)
      .sort((a, b) => (a.priority as number) - (b.priority as number))
      .map((item) => item.deal);
    return (withPriority.length > 0 ? withPriority : filtered).slice(0, PROMO_COUNT);
  }, [filtered, participants]);

  const popular = useMemo(
    () => [...filtered].sort((a, b) => participants(b.id) - participants(a.id)).slice(0, POPULAR_COUNT),
    [filtered, participants],
  );

  const notable = useMemo(() => filtered.slice(0, NOTABLE_COUNT), [filtered]);

  const closingSoon = useMemo(
    () => [...filtered].sort((a, b) => a.daysLeft - b.daysLeft).slice(0, CLOSING_SOON_COUNT),
    [filtered],
  );

  const upcoming = useMemo(() => {
    const scheduled = category === "전체" ? scheduledDeals() : scheduledDeals().filter((deal) => deal.category === category);
    return scheduled.slice(0, UPCOMING_COUNT);
  }, [scheduledDeals, category]);

  const newArrivals = useMemo(() => [...filtered].reverse().slice(0, NEW_ARRIVAL_COUNT), [filtered]);

  useEffect(() => {
    setSlide(0);
  }, [category]);

  useEffect(() => {
    if (promoDeals.length <= 1) return;
    const timer = setTimeout(() => {
      setSlide((i) => (i + 1) % promoDeals.length);
    }, CAROUSEL_INTERVAL_MS);
    return () => clearTimeout(timer);
  }, [slide, promoDeals.length]);

  const activePromo = promoDeals[slide] ?? null;

  function handleSearchSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = searchValue.trim();
    router.push(trimmed.length > 0 ? `/explore?q=${encodeURIComponent(trimmed)}` : "/explore");
  }

  return (
    <div className="mx-auto max-w-6xl space-y-10 px-6 py-9">
      <div className="space-y-4">
        <form onSubmit={handleSearchSubmit} className="flex h-12 items-center gap-2.5 rounded-xl border border-wb-line bg-wb-surface px-4">
          <Search className="h-4 w-4 shrink-0 text-wb-secondary" />
          <input
            value={searchValue}
            onChange={(e) => setSearchValue(e.target.value)}
            placeholder="상품이나 생산자를 검색해보세요"
            className="w-full bg-transparent text-sm outline-none placeholder:text-wb-secondary"
          />
        </form>

        <nav className="flex flex-wrap items-center gap-x-1 gap-y-2 text-sm font-semibold text-wb-secondary">
          <div className="relative">
            <button
              onClick={() => setCategoryMenuOpen((v) => !v)}
              className="flex items-center gap-1 rounded-lg px-2.5 py-1.5 hover:bg-wb-canvas hover:text-wb-ink"
            >
              <LayoutGrid className="h-4 w-4" />
              카테고리
              <ChevronDown className="h-3.5 w-3.5" />
            </button>
            {categoryMenuOpen && (
              <>
                <button
                  aria-label="닫기"
                  onClick={() => setCategoryMenuOpen(false)}
                  className="fixed inset-0 z-10 cursor-default"
                />
                <div className="absolute left-0 top-full z-20 mt-1 w-40 space-y-0.5 rounded-xl border border-wb-line bg-wb-surface p-1.5 shadow-md">
                  {CATEGORIES.map((item) => (
                    <button
                      key={item}
                      onClick={() => {
                        setCategory(item);
                        setCategoryMenuOpen(false);
                      }}
                      className={`block w-full rounded-lg px-3 py-2 text-left text-sm font-semibold ${
                        category === item ? "bg-wb-light-green/60 text-wb-green" : "text-wb-ink hover:bg-wb-canvas"
                      }`}
                    >
                      {item}
                    </button>
                  ))}
                </div>
              </>
            )}
          </div>

          <Link href="/home" className="rounded-lg bg-wb-green px-2.5 py-1.5 text-white">
            홈
          </Link>

          {SUB_NAV.map((item) => (
            <Link key={item.label} href={item.href} className="rounded-lg px-2.5 py-1.5 hover:bg-wb-canvas hover:text-wb-ink">
              {item.label}
            </Link>
          ))}
        </nav>
      </div>

      <div>
        <p className="text-sm font-semibold text-wb-green">좋은 아침이에요</p>
        <h1 className="mt-1 text-3xl font-bold">가격을 알면, 구매가 달라져요</h1>
      </div>

      {filtered.length === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-wb-line py-16 text-center">
          <PackageSearch className="h-8 w-8 text-wb-secondary" strokeWidth={1.5} />
          <p className="text-sm font-semibold text-wb-secondary">이 카테고리엔 아직 공동구매가 없어요</p>
        </div>
      ) : (
        <>
          <section className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            {activePromo && (
              <div className="space-y-3 lg:col-span-2">
                <div className="flex items-center gap-2 text-wb-green">
                  <ArrowDownCircle className="h-5 w-5" />
                  <h2 className="text-lg font-bold">다음 가격 인하가 가장 가까워요</h2>
                </div>
                <PromoCarousel deals={promoDeals} slide={slide} onSelectSlide={setSlide} />
              </div>
            )}

            <div className="space-y-3">
              <SectionHeading
                icon={<Flame className="h-5 w-5" />}
                tone="text-wb-orange"
                title="지금 인기 중인 공동구매"
                href="/ranking"
              />
              <div className="space-y-2">
                {popular.map((deal, index) => (
                  <PopularDealRow key={deal.id} deal={deal} rank={index + 1} />
                ))}
              </div>
            </div>
          </section>

          <section className="space-y-3">
            <SectionHeading
              icon={<Sparkles className="h-5 w-5" />}
              tone="text-wb-green"
              title="주목할 만한 공동구매"
              subtitle="생산 과정과 가격을 투명하게 공개했어요"
              href="/explore"
            />
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
              {notable.map((deal) => (
                <DealCard key={deal.id} deal={deal} />
              ))}
            </div>
          </section>

          <section className="space-y-3">
            <SectionHeading
              icon={<Clock3 className="h-5 w-5" />}
              tone="text-wb-secondary"
              title="마감 임박 공동구매"
              subtitle="마감이 얼마 남지 않은 공동구매예요"
              href="/explore?sort=closing"
            />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {closingSoon.map((deal) => (
                <DealCard key={deal.id} deal={deal} />
              ))}
            </div>
          </section>

          {upcoming.length > 0 && (
            <section className="space-y-3">
              <SectionHeading
                icon={<CalendarClock className="h-5 w-5" />}
                tone="text-wb-secondary"
                title="진행 예정 공동구매"
                subtitle="곧 시작하는 공동구매를 미리 만나보세요"
                href="/explore?status=scheduled"
              />
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
                {upcoming.map((deal) => (
                  <DealCard key={deal.id} deal={deal} />
                ))}
              </div>
            </section>
          )}

          <section className="space-y-3">
            <SectionHeading
              icon={<Rocket className="h-5 w-5" />}
              tone="text-wb-green"
              title="신규 공동구매"
              subtitle="새로 올라온 공동구매예요"
              href="/explore?sort=new"
            />
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
              {newArrivals.map((deal) => (
                <DealCard key={deal.id} deal={deal} />
              ))}
            </div>
          </section>
        </>
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

function SectionHeading({
  icon,
  tone,
  title,
  subtitle,
  href,
}: {
  icon: React.ReactNode;
  tone: string;
  title: string;
  subtitle?: string;
  href: string;
}) {
  return (
    <div className="flex items-end justify-between gap-3">
      <div className="flex items-center gap-2">
        <span className={tone}>{icon}</span>
        <div>
          <h2 className="text-lg font-bold">{title}</h2>
          {subtitle && <p className="text-sm text-wb-secondary">{subtitle}</p>}
        </div>
      </div>
      <Link href={href} className="shrink-0 text-xs font-bold text-wb-secondary hover:text-wb-green">
        전체보기
      </Link>
    </div>
  );
}

function PromoCarousel({
  deals,
  slide,
  onSelectSlide,
}: {
  deals: Deal[];
  slide: number;
  onSelectSlide: (index: number) => void;
}) {
  const deal = deals[slide];
  const { participants } = useDemoStore();
  const people = participants(deal.id);
  const tier = activeTier(deal, people);
  const next = nextTier(deal, people);

  function goTo(index: number) {
    onSelectSlide((index + deals.length) % deals.length);
  }

  return (
    <div className="space-y-3">
      <div className="relative">
        <Link
          href={`/deals/${deal.id}`}
          className="grid gap-6 rounded-2xl border border-wb-line bg-wb-surface p-6 transition-shadow hover:shadow-md md:grid-cols-2"
        >
          <DealArtwork deal={deal} className="h-52 w-full md:h-full" />
          <div className="flex flex-col">
            <div className="flex flex-wrap gap-2">
              <Tag highlighted>마감 D-{deal.daysLeft}</Tag>
              {next ? (
                <Tag>가격 인하까지 {(next.minimumPeople - people).toLocaleString("ko-KR")}명</Tag>
              ) : (
                <Tag>지금 최저가로 진행 중</Tag>
              )}
            </div>
            <h3 className="mt-4 text-2xl font-bold">{deal.title}</h3>
            <p className="mt-1.5 text-sm font-medium text-wb-secondary">{deal.producer}</p>
            <p className="mt-2 line-clamp-2 text-sm text-wb-secondary">{deal.summary}</p>
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

        {deals.length > 1 && (
          <>
            <button
              onClick={() => goTo(slide - 1)}
              aria-label="이전 홍보 콘텐츠"
              className="absolute left-3 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full border border-wb-line bg-wb-surface/90 shadow-sm hover:bg-wb-canvas"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <button
              onClick={() => goTo(slide + 1)}
              aria-label="다음 홍보 콘텐츠"
              className="absolute right-3 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full border border-wb-line bg-wb-surface/90 shadow-sm hover:bg-wb-canvas"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </>
        )}
      </div>

      {deals.length > 1 && (
        <div className="flex justify-center gap-1.5">
          {deals.map((item, index) => (
            <button
              key={item.id}
              onClick={() => onSelectSlide(index)}
              aria-label={`${index + 1}번째 홍보 콘텐츠 보기`}
              className={`h-1.5 rounded-full transition-all ${
                index === slide ? "w-6 bg-wb-green" : "w-1.5 bg-wb-line"
              }`}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function PopularDealRow({ deal, rank }: { deal: Deal; rank: number }) {
  const { participants } = useDemoStore();
  const people = participants(deal.id);
  const price = activeTier(deal, people).price;

  return (
    <Link
      href={`/deals/${deal.id}`}
      className="flex items-center gap-3 rounded-xl border border-wb-line bg-wb-surface p-2.5 transition-shadow hover:shadow-md"
    >
      <div className="relative shrink-0">
        <DealArtwork deal={deal} className="h-14 w-14" />
        <span
          className={`absolute -left-1.5 -top-1.5 flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-extrabold text-white ${
            rank <= 3 ? "bg-wb-orange" : "bg-wb-ink/70"
          }`}
        >
          {rank}
        </span>
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-bold">{deal.title}</p>
        <p className="mt-0.5 truncate text-xs text-wb-secondary">{deal.producer}</p>
      </div>
      <div className="shrink-0 text-right">
        <p className="text-sm font-bold">{won(price)}</p>
        <p className="text-[11px] font-bold text-wb-green">{people.toLocaleString("ko-KR")}명</p>
      </div>
    </Link>
  );
}
