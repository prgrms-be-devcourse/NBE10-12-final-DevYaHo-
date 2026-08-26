export type BarChartPoint = {
  label: string;
  value: number;
};

// 외부 차트 라이브러리 의존성 없이 ProgressBar처럼 인라인 SVG로 그리는 단순 막대그래프.
export function BarChart({ data, height = 120 }: { data: BarChartPoint[]; height?: number }) {
  const max = Math.max(1, ...data.map((d) => d.value));
  const barWidth = data.length > 0 ? 100 / data.length : 0;

  return (
    <div>
      <svg viewBox={`0 0 100 ${height}`} preserveAspectRatio="none" className="h-32 w-full overflow-visible">
        {data.map((point, i) => {
          const barHeight = (point.value / max) * (height - 4);
          return (
            <rect
              key={i}
              x={i * barWidth + barWidth * 0.15}
              y={height - barHeight}
              width={barWidth * 0.7}
              height={barHeight}
              rx={1}
              className="fill-wb-green/70"
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
