"use client";

import { useState } from "react";

export type ParticipationChartPoint = {
  label: string;
  fullLabel: string;
  value: number;
};

// 참여 수량 추이 그래프 - BarChart(components/ui)를 그대로 쓰지 않고 SettlementTrendChart와 동일하게
// 전용 컴포넌트로 만든 이유는, 호버 시 그 날짜의 참여 수량을 툴팁으로 보여줘야 해서다.
export function ParticipationTrendChart({
  data,
  height = 120,
}: {
  data: ParticipationChartPoint[];
  height?: number;
}) {
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);
  const max = Math.max(1, ...data.map((d) => d.value));
  const barWidth = data.length > 0 ? 100 / data.length : 0;
  const hovered = hoverIndex !== null ? data[hoverIndex] : null;

  return (
    <div className="relative">
      {hovered && (
        <div
          className="pointer-events-none absolute -top-2 z-10 -translate-x-1/2 -translate-y-full whitespace-nowrap rounded-lg bg-wb-ink px-3 py-2 text-xs text-white shadow-lg"
          style={{ left: `${(hoverIndex! + 0.5) * barWidth}%` }}
        >
          <p className="font-bold">{hovered.fullLabel}</p>
          <p className="mt-0.5 text-white/80">{hovered.value.toLocaleString("ko-KR")}개 참여</p>
        </div>
      )}
      <svg viewBox={`0 0 100 ${height}`} preserveAspectRatio="none" className="h-32 w-full overflow-visible">
        {data.map((point, i) => {
          const barHeight = Math.max((point.value / max) * (height - 4), 1);
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
      <div className="mt-1 flex justify-between text-[10px] text-wb-secondary">
        <span>{data[0]?.label ?? ""}</span>
        <span>{data[data.length - 1]?.label ?? ""}</span>
      </div>
    </div>
  );
}
