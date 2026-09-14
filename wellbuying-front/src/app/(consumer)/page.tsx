"use client";

import { useEffect, useMemo, useState } from "react";
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
} from "lucide-react";
import { GroupBuyArtwork } from "@/components/deal/GroupBuyArtwork";
import { GroupBuyCard } from "@/components/deal/GroupBuyCard";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { Tag } from "@/components/ui/Tag";
import { won } from "@/lib/format";
import { useGroupBuyList, type GroupBuyCardView } from "@/lib/groupBuy/useGroupBuyList";

const CAROUSEL_INTERVAL_MS = 4500;
const PROMO_COUNT = 3;
const POPULAR_SIDEBAR_COUNT = 5;
const CLOSING_SOON_COUNT = 8;
const UPCOMING_COUNT = 4;
const NEW_ARRIVAL_COUNT = 4;

export default function HomePage() {
  const { items: ongoing, loading: ongoingLoading } = useGroupBuyList("ONGOING");
  // 인기/신규/마감임박은 각각 다른 기준으로 정렬돼야 해서, 이미 받아온 ongoing 목록(서버 기본 정렬)을
  // 그대로 재활용하지 않고 정렬 기준별로 따로 받아온다 - 전체 진행중 건수가 한 번에 받는 개수(50)보다
  // 많아지면 클라이언트에서 재정렬해서는 진짜 상위 항목을 놓칠 수 있기 때문(서버가 정렬한 뒤 잘라줘야
  // 정확하다). 목록 카드에 필요한 필드는 동일해 사이즈만 작게 준다
  const { items: popularSource } = useGroupBuyList("ONGOING", {
    sort: "viewCount,desc",
    size: POPULAR_SIDEBAR_COUNT * 4,
  });
  const { items: closingSource } = useGroupBuyList("ONGOING", { sort: "endAt,asc", size: CLOSING_SOON_COUNT * 4 });
  const { items: newArrivalSource } = useGroupBuyList("ONGOING", {
    sort: "createdAt,desc",
    size: NEW_ARRIVAL_COUNT * 4,
  });
  const { items: upcomingAll } = useGroupBuyList("READY");
  const [slide, setSlide] = useState(0);

  const filtered = ongoing;

  const promoDeals = useMemo(() => filtered.slice(0, PROMO_COUNT), [filtered]);

  const closingSoon = useMemo(() => closingSource.slice(0, CLOSING_SOON_COUNT), [closingSource]);

  const upcoming = useMemo(() => upcomingAll.slice(0, UPCOMING_COUNT), [upcomingAll]);

  const newArrivals = useMemo(() => newArrivalSource.slice(0, NEW_ARRIVAL_COUNT), [newArrivalSource]);

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
      {ongoingLoading ? (
        <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : filtered.length === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-wb-line py-16 text-center">
          <PackageSearch className="h-8 w-8 text-wb-secondary" strokeWidth={1.5} />
          <p className="text-sm font-semibold text-wb-secondary">아직 진행 중인 공동구매가 없어요</p>
        </div>
      ) : (
        <>
          <section className="grid grid-cols-1 gap-6 lg:grid-cols-3 lg:items-stretch">
            {activePromo && (
              <div className="space-y-3 lg:col-span-2">
                <div className="flex items-center justify-between gap-3">
                  <div className="flex items-center gap-2 text-wb-green">
                    <ArrowDownCircle className="h-5 w-5" />
                    <h2 className="text-lg font-bold">지금 주목할 공동구매</h2>
                  </div>
                  <Link href="/explore" className="shrink-0 text-xs font-bold text-wb-secondary hover:text-wb-green">
                    전체보기
                  </Link>
                </div>
                <PromoCarousel items={promoDeals} slide={slide} onSelectSlide={setSlide} />
              </div>
            )}

            <div className="flex h-full flex-col space-y-3">
              <SectionHeading
                icon={<Flame className="h-5 w-5" />}
                tone="text-wb-orange"
                title="지금 인기 중인 공동구매 TOP 10"
                subtitle="가장 많이 조회된 공동구매예요"
                href="/ranking"
              />
              <div className="flex flex-1 flex-col gap-2">
                {popularSource.slice(0, POPULAR_SIDEBAR_COUNT).map((item, index) => (
                  <div key={item.id} className="flex-1">
                    <PopularDealRow item={item} rank={index + 1} />
                  </div>
                ))}
              </div>
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
  const [thumbnailFailed, setThumbnailFailed] = useState(false);

  useEffect(() => {
    setThumbnailFailed(false);
  }, [item.id]);

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
          {item.thumbnailUrl && !thumbnailFailed ? (
            // eslint-disable-next-line @next/next/no-img-element -- 판매자가 등록한 외부 썸네일 URL이라 next/image 최적화 대상이 아님
            <img
              src={item.thumbnailUrl}
              alt={item.title}
              className="h-52 w-full rounded-2xl object-cover md:h-full"
              onError={() => setThumbnailFailed(true)}
            />
          ) : (
            <GroupBuyArtwork entry={item} className="h-52 w-full md:h-full" />
          )}
          <div className="flex min-w-0 flex-col overflow-hidden">
            <div className="flex h-6 flex-wrap gap-2 overflow-hidden">
              <Tag highlighted>마감 D-{item.daysLeft}</Tag>
            </div>
            <h3 className="mt-4 line-clamp-2 h-16 text-2xl leading-8">{item.title}</h3>
            <p className="mt-2 line-clamp-2 h-10 text-sm text-wb-secondary">{item.summary}</p>
            <p className="mt-1.5 truncate text-xs text-wb-secondary">{item.producerName}</p>
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
  const [thumbnailFailed, setThumbnailFailed] = useState(false);
  const progress = item.maxQuantity > 0 ? item.currentQuantity / item.maxQuantity : 0;
  return (
    <Link
      href={`/deals/${item.id}`}
      className="flex items-center gap-3 rounded-xl border border-wb-line bg-wb-surface p-2.5 transition-shadow hover:shadow-md"
    >
      <div className="relative shrink-0">
        {item.thumbnailUrl && !thumbnailFailed ? (
          // eslint-disable-next-line @next/next/no-img-element -- 판매자가 등록한 외부 썸네일 URL이라 next/image 최적화 대상이 아님
          <img
            src={item.thumbnailUrl}
            alt={item.title}
            className="h-14 w-14 rounded-2xl object-cover"
            onError={() => setThumbnailFailed(true)}
          />
        ) : (
          <GroupBuyArtwork entry={item} className="h-14 w-14" />
        )}
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
        <div className="mt-1.5">
          <ProgressBar value={progress} />
        </div>
      </div>
      <div className="shrink-0 text-right">
        <p className="text-sm font-bold">{won(item.currentUnitPrice)}</p>
        <p className="mt-0.5 text-xs font-bold text-wb-green">{Math.round(progress * 100)}%</p>
      </div>
    </Link>
  );
}
