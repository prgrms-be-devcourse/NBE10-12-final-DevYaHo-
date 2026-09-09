"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  ArrowDownCircle,
  ArrowRight,
  CalendarClock,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Flame,
  PackageSearch,
  Quote,
  Rocket,
  Sparkles,
} from "lucide-react";
import { DealsSubNav } from "@/components/consumer/DealsSubNav";
import { GroupBuyArtwork } from "@/components/deal/GroupBuyArtwork";
import { GroupBuyCard } from "@/components/deal/GroupBuyCard";
import { Button } from "@/components/ui/Button";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { Tag } from "@/components/ui/Tag";
import { CATALOG_CATEGORIES } from "@/lib/groupBuy/seedCatalog";
import { useGroupBuyList, type GroupBuyCardView } from "@/lib/groupBuy/useGroupBuyList";

const CAROUSEL_INTERVAL_MS = 4500;
const PROMO_COUNT = 3;
const POPULAR_COUNT = 4;
const NOTABLE_COUNT = 8;
const CLOSING_SOON_COUNT = 8;
const UPCOMING_COUNT = 4;
const NEW_ARRIVAL_COUNT = 4;

export default function HomePage() {
  const { items: ongoing, loading: ongoingLoading } = useGroupBuyList("ONGOING");
  // 인기/신규/마감임박은 각각 다른 기준으로 정렬돼야 해서, 이미 받아온 ongoing 목록(서버 기본 정렬)을
  // 그대로 재활용하지 않고 정렬 기준별로 따로 받아온다 - 전체 진행중 건수가 한 번에 받는 개수(50)보다
  // 많아지면 클라이언트에서 재정렬해서는 진짜 상위 항목을 놓칠 수 있기 때문(서버가 정렬한 뒤 잘라줘야
  // 정확하다). 목록 카드에 필요한 필드는 동일해 사이즈만 작게 준다
  const {
    items: popularSource,
    hasMore: popularHasMore,
    loadingMore: popularLoadingMore,
    loadMore: loadMorePopular,
  } = useGroupBuyList("ONGOING", { sort: "viewCount,desc", size: POPULAR_COUNT * 4 });
  const {
    items: closingSource,
    hasMore: closingHasMore,
    loadingMore: closingLoadingMore,
    loadMore: loadMoreClosing,
  } = useGroupBuyList("ONGOING", { sort: "endAt,asc", size: CLOSING_SOON_COUNT * 4 });
  const {
    items: newArrivalSource,
    hasMore: newArrivalHasMore,
    loadingMore: newArrivalLoadingMore,
    loadMore: loadMoreNewArrival,
  } = useGroupBuyList("ONGOING", { sort: "createdAt,desc", size: NEW_ARRIVAL_COUNT * 4 });
  const { items: upcomingAll } = useGroupBuyList("READY");
  const [category, setCategory] = useState("전체");
  const [slide, setSlide] = useState(0);
  const [popularVisible, setPopularVisible] = useState(POPULAR_COUNT);
  const [closingVisible, setClosingVisible] = useState(CLOSING_SOON_COUNT);
  const [newArrivalVisible, setNewArrivalVisible] = useState(NEW_ARRIVAL_COUNT);

  const byCategory = useCallback(
    (items: GroupBuyCardView[]) => (category === "전체" ? items : items.filter((item) => item.category === category)),
    [category],
  );

  const filtered = byCategory(ongoing);

  const promoDeals = useMemo(() => filtered.slice(0, PROMO_COUNT), [filtered]);

  const popularFiltered = useMemo(() => byCategory(popularSource), [popularSource, byCategory]);
  const popular = useMemo(() => popularFiltered.slice(0, popularVisible), [popularFiltered, popularVisible]);

  const notable = useMemo(() => filtered.slice(0, NOTABLE_COUNT), [filtered]);

  const closingFiltered = useMemo(() => byCategory(closingSource), [closingSource, byCategory]);
  const closingSoon = useMemo(() => closingFiltered.slice(0, closingVisible), [closingFiltered, closingVisible]);

  const upcoming = useMemo(() => {
    const scheduled = category === "전체" ? upcomingAll : upcomingAll.filter((item) => item.category === category);
    return scheduled.slice(0, UPCOMING_COUNT);
  }, [upcomingAll, category]);

  const newArrivalFiltered = useMemo(() => byCategory(newArrivalSource), [newArrivalSource, byCategory]);
  const newArrivals = useMemo(
    () => newArrivalFiltered.slice(0, newArrivalVisible),
    [newArrivalFiltered, newArrivalVisible],
  );

  useEffect(() => {
    setSlide(0);
    setPopularVisible(POPULAR_COUNT);
    setClosingVisible(CLOSING_SOON_COUNT);
    setNewArrivalVisible(NEW_ARRIVAL_COUNT);
  }, [category]);

  // 이미 받아온 버퍼(카테고리 필터링 후) 안에 더 보여줄 항목이 남아있으면 그것부터 보여주고,
  // 버퍼가 바닥나면 그때 서버에서 다음 페이지를 이어받는다
  function showMorePopular() {
    const next = popularVisible + POPULAR_COUNT;
    setPopularVisible(next);
    if (next > popularFiltered.length && popularHasMore) loadMorePopular();
  }

  function showMoreClosing() {
    const next = closingVisible + CLOSING_SOON_COUNT;
    setClosingVisible(next);
    if (next > closingFiltered.length && closingHasMore) loadMoreClosing();
  }

  function showMoreNewArrival() {
    const next = newArrivalVisible + NEW_ARRIVAL_COUNT;
    setNewArrivalVisible(next);
    if (next > newArrivalFiltered.length && newArrivalHasMore) loadMoreNewArrival();
  }

  useEffect(() => {
    if (promoDeals.length <= 1) return;
    const timer = setTimeout(() => {
      setSlide((i) => (i + 1) % promoDeals.length);
    }, CAROUSEL_INTERVAL_MS);
    return () => clearTimeout(timer);
  }, [slide, promoDeals.length]);

  const activePromo = promoDeals[slide] ?? null;

  return (
    <div className="mx-auto max-w-6xl space-y-16 px-6 py-9">
      <DealsSubNav categories={CATALOG_CATEGORIES} categoryValue={category} onCategoryChange={setCategory} />

      <div>
        <p className="text-sm font-semibold text-wb-green">좋은 아침이에요</p>
        <h1 className="mt-1 text-3xl font-bold">가격을 알면, 구매가 달라져요</h1>
      </div>

      {ongoingLoading ? (
        <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : filtered.length === 0 ? (
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
                  <h2 className="text-lg font-bold">지금 주목할 공동구매</h2>
                </div>
                <PromoCarousel items={promoDeals} slide={slide} onSelectSlide={setSlide} />
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
                {popular.map((item, index) => (
                  <PopularDealRow key={item.id} item={item} rank={index + 1} />
                ))}
              </div>
              {(popularFiltered.length > popularVisible || popularHasMore) && (
                <Button
                  variant="secondary"
                  className="w-full"
                  loading={popularLoadingMore}
                  onClick={showMorePopular}
                >
                  더보기
                </Button>
              )}
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
              {notable.map((item) => (
                <GroupBuyCard key={item.id} item={item} />
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
              {closingSoon.map((item) => (
                <GroupBuyCard key={item.id} item={item} />
              ))}
            </div>
            {(closingFiltered.length > closingVisible || closingHasMore) && (
              <Button variant="secondary" className="w-full" loading={closingLoadingMore} onClick={showMoreClosing}>
                더보기
              </Button>
            )}
          </section>

          <section className="space-y-3">
            <SectionHeading
              icon={<Rocket className="h-5 w-5" />}
              tone="text-wb-green"
              title="신규 공동구매"
              subtitle="새로 올라온 공동구매예요"
              href="/explore?sort=new"
            />
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
              {newArrivals.map((item) => (
                <GroupBuyCard key={item.id} item={item} />
              ))}
            </div>
            {(newArrivalFiltered.length > newArrivalVisible || newArrivalHasMore) && (
              <Button
                variant="secondary"
                className="w-full"
                loading={newArrivalLoadingMore}
                onClick={showMoreNewArrival}
              >
                더보기
              </Button>
            )}
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
                {upcoming.map((item) => (
                  <GroupBuyCard key={item.id} item={item} />
                ))}
              </div>
            </section>
          )}
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
  items,
  slide,
  onSelectSlide,
}: {
  items: GroupBuyCardView[];
  slide: number;
  onSelectSlide: (index: number) => void;
}) {
  const item = items[slide];

  function goTo(index: number) {
    onSelectSlide((index + items.length) % items.length);
  }

  return (
    <div className="space-y-3">
      <div className="relative">
        <Link
          href={`/deals/${item.id}`}
          className="grid gap-6 rounded-2xl border border-wb-line bg-wb-surface p-6 transition-shadow hover:shadow-md md:h-96 md:grid-cols-2"
        >
          <GroupBuyArtwork entry={item} className="h-52 w-full md:h-full" />
          <div className="flex min-w-0 flex-col overflow-hidden">
            <div className="flex h-6 flex-wrap gap-2 overflow-hidden">
              <Tag highlighted>마감 D-{item.daysLeft}</Tag>
            </div>
            <h3 className="mt-4 line-clamp-2 h-16 text-2xl leading-8">{item.title}</h3>
            <p className="mt-1.5 truncate text-sm font-medium text-wb-secondary">{item.producerName}</p>
            <p className="mt-2 line-clamp-2 h-10 text-sm text-wb-secondary">{item.summary}</p>
            <div className="mt-auto space-y-3 pt-4">
              <ProgressBar value={item.maxQuantity === 0 ? 0 : item.currentQuantity / item.maxQuantity} />
              <div className="flex justify-between text-xs">
                <span className="font-bold text-wb-green">{item.currentQuantity.toLocaleString("ko-KR")}개 참여</span>
                <span className="text-wb-secondary">목표 {item.maxQuantity.toLocaleString("ko-KR")}개</span>
              </div>
              <div className="flex items-center justify-end gap-1.5 text-xs font-bold text-wb-green">
                공동구매 자세히 보기
                <ArrowRight className="h-3.5 w-3.5" />
              </div>
            </div>
          </div>
        </Link>

        {items.length > 1 && (
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

      {items.length > 1 && (
        <div className="flex justify-center gap-1.5">
          {items.map((entry, index) => (
            <button
              key={entry.id}
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

function PopularDealRow({ item, rank }: { item: GroupBuyCardView; rank: number }) {
  return (
    <Link
      href={`/deals/${item.id}`}
      className="flex items-center gap-3 rounded-xl border border-wb-line bg-wb-surface p-2.5 transition-shadow hover:shadow-md"
    >
      <div className="relative shrink-0">
        <GroupBuyArtwork entry={item} className="h-14 w-14" />
        <span
          className={`absolute -left-1.5 -top-1.5 flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-extrabold text-white ${
            rank <= 3 ? "bg-wb-orange" : "bg-wb-ink/70"
          }`}
        >
          {rank}
        </span>
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm">{item.title}</p>
        <p className="mt-0.5 truncate text-xs text-wb-secondary">{item.producerName}</p>
      </div>
      <div className="shrink-0 text-right">
        <p className="text-sm font-bold text-wb-green">조회 {item.viewCount.toLocaleString("ko-KR")}회</p>
        <p className="mt-0.5 text-xs text-wb-secondary">{item.currentQuantity.toLocaleString("ko-KR")}개 참여</p>
      </div>
    </Link>
  );
}
