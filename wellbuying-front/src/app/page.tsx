"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowRight, PackageSearch, ShieldCheck, TrendingDown, Users } from "lucide-react";
import { DealCard } from "@/components/deal/DealCard";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { activeTier, won } from "@/lib/mock/types";

const HOW_IT_WORKS = [
  {
    step: "1",
    title: "생산자가 원가를 공개해요",
    description: "이 가격에 이 정도 이익이 붙는지, 시작 전부터 투명하게 보여드려요.",
  },
  {
    step: "2",
    title: "모일수록 가격이 내려가요",
    description: "참여자가 구간을 넘길 때마다 단가가 낮아지는 걸 실시간으로 확인할 수 있어요.",
  },
  {
    step: "3",
    title: "전원 같은 최종가로 확정돼요",
    description: "먼저 참여했더라도 마감 시점의 가장 낮은 구간가로 똑같이 소급 적용돼요.",
  },
];

function Logo({ className = "h-8 w-8" }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" className={className} stroke="currentColor" strokeWidth={2}>
      <path d="M9 12.75 11.25 15 15 9.75" strokeLinecap="round" strokeLinejoin="round" />
      <path
        d="M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6l7-3z"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export default function LandingPage() {
  const { status } = useAuth();
  const router = useRouter();
  const { visibleDeals, participants } = useDemoStore();
  const [category, setCategory] = useState("전체");

  useEffect(() => {
    if (status === "authenticated") router.replace("/home");
  }, [status, router]);

  const deals = visibleDeals();
  const categories = useMemo(() => ["전체", ...new Set(deals.map((deal) => deal.category))], [deals]);
  const filtered = category === "전체" ? deals : deals.filter((deal) => deal.category === category);

  return (
    <div className="flex flex-1 flex-col">
      <header className="sticky top-0 z-10 border-b border-wb-line bg-wb-surface/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-3.5">
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-wb-green text-white">
              <Logo className="h-5 w-5" />
            </div>
            <span className="text-lg font-extrabold tracking-tight">WellBuying</span>
          </div>
          <div className="flex items-center gap-2">
            <Link
              href="/login"
              className="hidden h-10 items-center rounded-lg px-4 text-sm font-semibold text-wb-ink hover:bg-wb-canvas sm:flex"
            >
              로그인
            </Link>
            <Link
              href="/login"
              className="flex h-10 items-center rounded-lg bg-wb-green px-4 text-sm font-semibold text-white transition-colors hover:bg-wb-green/90"
            >
              시작하기
            </Link>
          </div>
        </div>
      </header>

      <main className="flex-1">
        <section className="border-b border-wb-line bg-wb-surface">
          <div className="mx-auto max-w-6xl px-6 py-16 sm:py-24">
            <div className="max-w-2xl">
              <span className="inline-flex items-center rounded-full bg-wb-light-green/60 px-3 py-1 text-xs font-bold text-wb-green">
                생산자가 공개하는 진짜 가격
              </span>
              <h1 className="mt-5 text-4xl font-extrabold leading-tight tracking-tight sm:text-5xl">
                가격을 알면,
                <br />
                구매가 달라져요
              </h1>
              <p className="mt-5 text-base text-wb-secondary sm:text-lg">
                원가와 이익을 숨기지 않는 생산자의 공동구매예요. 참여자가 모일수록 단가가 낮아지고,
                마감 시점엔 모두가 가장 낮은 가격으로 확정돼요.
              </p>
              <div className="mt-8 flex flex-wrap gap-3">
                <a
                  href="#live-deals"
                  className="flex h-12 items-center gap-1.5 rounded-lg bg-wb-green px-6 text-sm font-bold text-white transition-colors hover:bg-wb-green/90"
                >
                  공동구매 둘러보기
                  <ArrowRight className="h-4 w-4" />
                </a>
                <Link
                  href="/login"
                  className="flex h-12 items-center rounded-lg border border-wb-line bg-wb-surface px-6 text-sm font-bold text-wb-ink hover:bg-wb-canvas"
                >
                  생산자로 시작하기
                </Link>
              </div>
            </div>

            <div className="mt-14 grid grid-cols-1 gap-4 sm:grid-cols-3">
              {[
                { icon: ShieldCheck, title: "투명한 원가 공개", text: "이익률까지 숨김없이 보여드려요" },
                { icon: TrendingDown, title: "모일수록 저렴하게", text: "참여자 구간마다 단가가 낮아져요" },
                { icon: Users, title: "생산자와 직접 연결", text: "중간 유통 단계를 덜어냈어요" },
              ].map((item) => (
                <div key={item.title} className="flex items-start gap-3 rounded-xl bg-wb-canvas p-4">
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-wb-green/10 text-wb-green">
                    <item.icon className="h-4.5 w-4.5" strokeWidth={2} />
                  </div>
                  <div>
                    <p className="text-sm font-bold">{item.title}</p>
                    <p className="mt-0.5 text-xs text-wb-secondary">{item.text}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section id="live-deals" className="mx-auto max-w-6xl space-y-5 px-6 py-14 scroll-mt-16">
          <div>
            <h2 className="text-2xl font-extrabold">지금 모이고 있어요</h2>
            <p className="mt-1 text-sm text-wb-secondary">생산 과정과 가격을 투명하게 공개한 공동구매예요</p>
          </div>

          <div className="flex flex-wrap gap-2">
            {categories.map((c) => (
              <button
                key={c}
                onClick={() => setCategory(c)}
                className={`rounded-full px-4 py-2 text-xs font-semibold transition-colors ${
                  category === c ? "bg-wb-green text-white" : "bg-wb-canvas text-wb-secondary hover:bg-wb-tag-surface"
                }`}
              >
                {c}
              </button>
            ))}
          </div>

          {filtered.length === 0 ? (
            <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-wb-line py-16 text-center">
              <PackageSearch className="h-8 w-8 text-wb-secondary" strokeWidth={1.5} />
              <p className="text-sm font-semibold text-wb-secondary">이 카테고리엔 아직 공동구매가 없어요</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {filtered.map((deal) => (
                <DealCard key={deal.id} deal={deal} />
              ))}
            </div>
          )}
        </section>

        <section className="border-y border-wb-line bg-wb-surface">
          <div className="mx-auto max-w-6xl px-6 py-14">
            <h2 className="text-2xl font-extrabold">어떻게 가격이 내려가나요?</h2>
            <div className="mt-8 grid grid-cols-1 gap-8 sm:grid-cols-3">
              {HOW_IT_WORKS.map((item) => (
                <div key={item.step}>
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-wb-green text-sm font-extrabold text-white">
                    {item.step}
                  </div>
                  <p className="mt-3 text-base font-bold">{item.title}</p>
                  <p className="mt-1.5 text-sm text-wb-secondary">{item.description}</p>
                </div>
              ))}
            </div>

            {deals[0] && (
              <div className="mt-10 flex flex-wrap items-center gap-x-8 gap-y-2 rounded-xl bg-wb-canvas p-5 text-sm">
                <span className="font-bold text-wb-secondary">예시로 보면</span>
                {deals[0].tiers.map((tier, i) => (
                  <div key={tier.minimumPeople} className="flex items-center gap-2">
                    {i > 0 && <ArrowRight className="h-3.5 w-3.5 text-wb-secondary" />}
                    <span className="text-wb-secondary">{tier.minimumPeople.toLocaleString("ko-KR")}명</span>
                    <span className="font-bold text-wb-green">{won(tier.price)}</span>
                  </div>
                ))}
                <span className="text-wb-secondary">
                  ({deals[0].title} · 현재 {participants(deals[0].id).toLocaleString("ko-KR")}명 참여 중,{" "}
                  {won(activeTier(deals[0], participants(deals[0].id)).price)})
                </span>
              </div>
            )}
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-6 py-16 text-center">
          <h2 className="text-2xl font-extrabold sm:text-3xl">지금 바로 시작해보세요</h2>
          <p className="mt-2 text-sm text-wb-secondary sm:text-base">
            회원가입하고 진행 중인 공동구매에 참여해보세요.
          </p>
          <Link
            href="/login"
            className="mt-6 inline-flex h-12 items-center gap-1.5 rounded-lg bg-wb-green px-8 text-sm font-bold text-white transition-colors hover:bg-wb-green/90"
          >
            무료로 시작하기
            <ArrowRight className="h-4 w-4" />
          </Link>
        </section>
      </main>

      <footer className="border-t border-wb-line px-6 py-8 text-center text-xs text-wb-secondary">
        WellBuying · 가격을 알면, 구매가 달라져요
      </footer>
    </div>
  );
}
