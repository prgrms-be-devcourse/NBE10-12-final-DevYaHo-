"use client";

import { useState } from "react";
import type { AdminSettlementTrendPointResponse } from "@/lib/api/types";
import { won } from "@/lib/format";

function shortLabel(periodStart: string): string {
  const d = new Date(periodStart);
  return `${String(d.getFullYear()).slice(2)}.${String(d.getMonth() + 1).padStart(2, "0")}`;
}

function fullLabel(periodStart: string): string {
  const d = new Date(periodStart);
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월`;
}

// 관리자 매출 추이 그래프 - producer/SettlementTrendChart와 같은 골격이지만, 막대를 총 매출(payout,
// 초록) + 수수료(orange) 두 구간으로 쌓아 올려서 호버 시 둘 다 확인할 수 있게 한다.
export function AdminSettlementTrendChart({
  data,
  height = 120,
}: {
  data: AdminSettlementTrendPointResponse[];
  height?: number;
}) {
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);
  const max = Math.max(1, ...data.map((d) => d.totalSales));
  const barWidth = data.length > 0 ? 100 / data.length : 0;
  const hovered = hoverIndex !== null ? data[hoverIndex] : null;

  return (
    <div className="relative">
      <div className="mb-2 flex items-center gap-4 text-[11px] text-wb-secondary">
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-sm bg-wb-green/70" /> 지급액
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-sm bg-orange-400" /> 플랫폼 수수료
        </span>
      </div>
      {hovered && (
        <div
          className="pointer-events-none absolute top-6 z-10 -translate-x-1/2 -translate-y-full whitespace-nowrap rounded-lg bg-wb-ink px-3 py-2 text-xs text-white shadow-lg"
          style={{ left: `${(hoverIndex! + 0.5) * barWidth}%` }}
        >
          <p className="font-bold">{fullLabel(hovered.periodStart)}</p>
          <p className="mt-0.5 text-white/80">총 매출 {won(hovered.totalSales)}</p>
          <p className="text-orange-300">수수료 {won(hovered.platformFee)}</p>
          <p className="text-white/60">{hovered.groupBuyCount}건</p>
        </div>
      )}
      <svg viewBox={`0 0 100 ${height}`} preserveAspectRatio="none" className="h-32 w-full overflow-visible">
        {data.map((point, i) => {
          const totalHeight = Math.max((point.totalSales / max) * (height - 4), point.totalSales > 0 ? 1 : 0);
          const feeHeight = point.totalSales > 0 ? (point.platformFee / point.totalSales) * totalHeight : 0;
          const payoutHeight = totalHeight - feeHeight;
          const x = i * barWidth + barWidth * 0.15;
          const w = barWidth * 0.7;
          const isHovered = hoverIndex === i;
          return (
            <g
              key={i}
              className="cursor-pointer"
              onMouseEnter={() => setHoverIndex(i)}
              onMouseLeave={() => setHoverIndex(null)}
            >
              <rect
                x={x}
                y={height - payoutHeight}
                width={w}
                height={payoutHeight}
                className={`transition-colors ${isHovered ? "fill-wb-green" : "fill-wb-green/70"}`}
              />
              <rect
                x={x}
                y={height - totalHeight}
                width={w}
                height={feeHeight}
                rx={feeHeight > 1 ? 1 : 0}
                className={`transition-colors ${isHovered ? "fill-orange-500" : "fill-orange-400/80"}`}
              />
            </g>
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
