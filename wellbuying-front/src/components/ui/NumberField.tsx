"use client";

export function NumberField({
  label,
  suffix,
  value,
  onChange,
  min,
  compact,
}: {
  label: string;
  suffix: string;
  value: number;
  onChange: (value: number) => void;
  min?: number;
  compact?: boolean;
}) {
  if (compact) {
    return (
      <label className="block">
        <span className="mb-1 block text-[10px] text-wb-secondary">{label}</span>
        <div className="flex items-center gap-1 rounded-lg border border-wb-line bg-wb-surface px-2.5 py-2">
          <input
            type="number"
            min={min}
            value={value}
            onChange={(e) => onChange(Number(e.target.value) || 0)}
            className="w-full bg-transparent text-sm font-bold outline-none"
          />
          <span className="shrink-0 text-[10px] text-wb-secondary">{suffix}</span>
        </div>
      </label>
    );
  }

  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-xs font-bold text-wb-ink">{label}</span>
      <div className="flex h-11 items-center gap-1 rounded-lg border border-wb-line bg-wb-canvas px-3 focus-within:border-wb-green focus-within:ring-1 focus-within:ring-wb-green">
        <input
          type="number"
          min={min}
          value={value}
          onChange={(e) => onChange(Number(e.target.value) || 0)}
          className="w-full bg-transparent text-sm text-wb-ink outline-none"
        />
        <span className="shrink-0 text-sm text-wb-secondary">{suffix}</span>
      </div>
    </label>
  );
}
