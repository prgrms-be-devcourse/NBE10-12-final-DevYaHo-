"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { RadioTower } from "lucide-react";
import { GroupBuyStatusTag } from "@/components/groupbuy/GroupBuyStatusTag";
import { Banner } from "@/components/ui/Banner";
import { EmptyState } from "@/components/ui/EmptyState";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { listGroupBuys } from "@/lib/api/groupBuy";
import { ApiError } from "@/lib/api/http";
import type { GroupBuyStatus, GroupBuySummaryResponse } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";

const STATUS_FILTERS: { value: GroupBuyStatus | "ALL"; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "ONGOING", label: "모집 중" },
  { value: "READY", label: "오픈 예정" },
  { value: "SUCCESS", label: "성사" },
  { value: "FAILED", label: "목표 미달" },
];

export default function LiveGroupBuysPage() {
  const [status, setStatus] = useState<GroupBuyStatus | "ALL">("ONGOING");
  const [items, setItems] = useState<GroupBuySummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        const page = await listGroupBuys({ status: status === "ALL" ? undefined : status, size: 30 });
        if (!ignore) setItems(page.content);
      } catch (e) {
        if (!ignore) setError(e instanceof ApiError ? e.message : "목록을 불러오지 못했어요.");
      } finally {
        if (!ignore) setLoading(false);
      }
    }

    load();
    return () => {
      ignore = true;
    };
  }, [status]);

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-6 py-9">
      <div className="flex items-center gap-2.5">
        <RadioTower className="h-6 w-6 text-wb-green" />
        <div>
          <h1 className="text-3xl font-bold">라이브 공동구매</h1>
          <p className="mt-1 text-sm text-wb-secondary">
            실제 서버(B트랙 공동구매 API)에 연결된 데이터예요. 목업 데이터와는 별도로 동작해요.
          </p>
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        {STATUS_FILTERS.map((filter) => (
          <button
            key={filter.value}
            onClick={() => setStatus(filter.value)}
            className={`rounded-full px-4 py-2 text-xs font-semibold transition-colors ${
              status === filter.value ? "bg-wb-green text-white" : "bg-wb-canvas text-wb-secondary"
            }`}
          >
            {filter.label}
          </button>
        ))}
      </div>

      {error && <Banner tone="error">{error}</Banner>}

      {loading ? (
        <p className="py-16 text-center text-sm text-wb-secondary">불러오는 중...</p>
      ) : items.length === 0 ? (
        <EmptyState
          icon={RadioTower}
          title="표시할 공동구매가 없어요"
          message="다른 상태 필터를 선택하거나, 생산자 페이지에서 새 공동구매를 만들어보세요."
        />
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {items.map((item) => (
            <Link
              key={item.id}
              href={`/live-groupbuys/${item.id}`}
              className="block space-y-3 rounded-xl border border-wb-line bg-wb-surface p-4 transition-shadow hover:shadow-md"
            >
              <div className="flex items-center justify-between">
                <GroupBuyStatusTag status={item.status} />
                <span className="text-xs text-wb-secondary">상품 #{item.productId}</span>
              </div>
              <p className="line-clamp-2 text-base font-bold">{item.title}</p>
              <ProgressBar value={item.maxQuantity === 0 ? 0 : item.currentQuantity / item.maxQuantity} />
              <div className="flex items-center justify-between text-xs text-wb-secondary">
                <span>
                  {item.currentQuantity.toLocaleString("ko-KR")} / {item.maxQuantity.toLocaleString("ko-KR")}개
                </span>
                <span>{formatDateTime(item.endAt)} 마감</span>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
