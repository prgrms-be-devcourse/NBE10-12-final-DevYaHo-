"use client";

import { useEffect, useMemo, useState } from "react";
import { PackageSearch } from "lucide-react";
import { DealsSubNav } from "@/components/consumer/DealsSubNav";
import { GroupBuyCard } from "@/components/deal/GroupBuyCard";
import { Button } from "@/components/ui/Button";
import { useGroupBuyList, type GroupBuyCardView } from "@/lib/groupBuy/useGroupBuyList";

const PAGE_SIZE = 12;

export default function RankingPage() {
  const { items, loading, page, totalPages, setPage } = useGroupBuyList("ONGOING", {
    sort: "viewCount,desc",
    size: PAGE_SIZE,
  });
  const [category, setCategory] = useState("전체");

  // 카테고리를 바꾸면 이전 페이지 번호가 새 필터 기준으로는 의미가 없으므로 1페이지로 되돌린다
  useEffect(() => {
    setPage(0);
  }, [category, setPage]);

  // 서버에 카테고리 필터 파라미터가 없어, 현재 페이지에 이미 불러온(viewCount 내림차순) 항목 안에서만 걸러낸다
  const ranked = useMemo(
    () => (category === "전체" ? items : items.filter((item) => item.category === category)),
    [items, category],
  );

  return (
    <div className="mx-auto max-w-6xl space-y-5 px-6 py-9">
      <DealsSubNav categoryValue={category} onCategoryChange={setCategory} />

      <div>
        <h1 className="text-3xl font-bold">인기 공동구매</h1>
        <p className="mt-1 text-sm text-wb-secondary">지금 가장 조회수가 높은 공동구매예요.</p>
      </div>

      {loading ? (
        <p className="py-20 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : ranked.length === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-wb-line py-16 text-center">
          <PackageSearch className="h-8 w-8 text-wb-secondary" strokeWidth={1.5} />
          <p className="text-sm font-semibold text-wb-secondary">이 카테고리엔 아직 공동구매가 없어요</p>
        </div>
      ) : (
        <>
          <p className="text-base font-bold">{ranked.length}개의 공동구매가 있어요</p>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {ranked.map((item, index) => (
              <RankedGroupBuyCard key={item.id} item={item} rank={page * PAGE_SIZE + index + 1} />
            ))}
          </div>
          {totalPages > 1 && (
            <div className="flex justify-center gap-2">
              <Button
                variant="secondary"
                className="px-3 py-1.5 text-xs"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                이전
              </Button>
              <span className="flex items-center px-2 text-xs text-wb-secondary">
                {page + 1} / {totalPages}
              </span>
              <Button
                variant="secondary"
                className="px-3 py-1.5 text-xs"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                다음
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function RankedGroupBuyCard({ item, rank }: { item: GroupBuyCardView; rank: number }) {
  return (
    <div className="relative">
      <span
        className={`absolute -left-1.5 -top-1.5 z-10 flex h-6 w-6 items-center justify-center rounded-full text-[11px] font-extrabold text-white ${
          rank <= 3 ? "bg-wb-orange" : "bg-wb-ink/70"
        }`}
      >
        {rank}
      </span>
      <GroupBuyCard item={item} />
      <p className="mt-1 text-xs text-wb-secondary">조회 {item.viewCount.toLocaleString("ko-KR")}회</p>
    </div>
  );
}
