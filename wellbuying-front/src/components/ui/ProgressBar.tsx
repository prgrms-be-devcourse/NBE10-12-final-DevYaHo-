export function ProgressBar({ value }: { value: number }) {
  const clamped = Math.min(Math.max(value, 0), 1) * 100;
  return (
    <div className="h-2 w-full overflow-hidden rounded-full bg-wb-green/10">
      <div className="h-full rounded-full bg-wb-green transition-all" style={{ width: `${clamped}%` }} />
    </div>
  );
}
