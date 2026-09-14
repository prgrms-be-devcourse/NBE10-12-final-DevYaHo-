"use client";

import { useState } from "react";
import type { SettlementTrendPointResponse } from "@/lib/api/types";
import { won } from "@/lib/format";

function shortLabel(periodStart: string): string {
  const d = new Date(periodStart);
  return `${String(d.getFullYear()).slice(2)}.${String(d.getMonth() + 1).padStart(2, "0")}`;
}

function fullLabel(periodStart: string): string {
  const d = new Date(periodStart);
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월`;
}

// 매출 추이 그래프 - BarChart(components/ui)를 그대로 쓰지 않고 이 페이지 전용으로 만든 이유는
// 막대마다 연/월 라벨을 다 보여줘야 하고(BarChart는 첫/끝 라벨만), 호버 시 그 달의 정산 내역
// (매출·건수)을 툴팁으로 보여줘야 해서다.
export function SettlementTrendChart({
  data,
  height = 120,
}: {
  data: SettlementTrendPointResponse[];
  height?: number;
}) {
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);
  const max = Math.max(1, ...data.map((d) => d.totalSales));
  const barWidth = data.length > 0 ? 100 / data.length : 0;
  const hovered = hoverIndex !== null ? data[hoverIndex] : null;

  return (
    <div className="relative">
      {hovered && (
        <div
          className="pointer-events-none absolute -top-2 z-10 -translate-x-1/2 -translate-y-full whitespace-nowrap rounded-lg bg-wb-ink px-3 py-2 text-xs text-white shadow-lg"
          style={{ left: `${(hoverIndex! + 0.5) * barWidth}%` }}
        >
          <p className="font-bold">{fullLabel(hovered.periodStart)}</p>
          <p className="mt-0.5 text-white/80">
            {won(hovered.totalSales)} · {hovered.groupBuyCount}건
          </p>
        </div>
      )}
      <svg viewBox={`0 0 100 ${height}`} preserveAspectRatio="none" className="h-32 w-full overflow-visible">
        {data.map((point, i) => {
          const barHeight = Math.max((point.totalSales / max) * (height - 4), 1);
          return (
            <rect
              key={i}
              x={i * barWidth + barWidth * 0.15}
              y={height - barHeight}
              width={barWidth * 0.7}
              height={barHeight}
              rx={1}
              className={`cursor-pointer transition-colors ${hoverIndex === i ? "fill-wb-green" : "fill-wb-green/70"}`}
              onMouseEnter={() => setHoverIndex(i)}
              onMouseLeave={() => setHoverIndex(null)}
            />
          );
        })}
      </svg>
      <div className="mt-1 flex">
        {data.map((point, i) => (
          <span
            key={i}
            style={{ width: `${barWidth}%` }}
            className={`text-center text-[9px] ${hoverIndex === i ? "font-bold text-wb-green" : "text-wb-secondary"}`}
          >
            {shortLabel(point.periodStart)}
          </span>
        ))}
      </div>
    </div>
  );
}
