"use client";

import { Search } from "lucide-react";

export function SearchBar({
  value,
  onChange,
  onSubmit,
  placeholder = "상품이나 생산자를 검색해보세요",
  size = "md",
}: {
  value: string;
  onChange: (value: string) => void;
  onSubmit?: (e: React.FormEvent) => void;
  placeholder?: string;
  size?: "sm" | "md";
}) {
  const input = (
    <>
      <Search className="h-4 w-4 shrink-0 text-wb-secondary" />
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full bg-transparent text-sm outline-none placeholder:text-wb-secondary"
      />
    </>
  );

  const className =
    size === "sm"
      ? "flex h-9 w-52 items-center gap-2 rounded-lg border border-wb-line bg-wb-surface px-3 text-sm md:w-64"
      : "flex h-12 items-center gap-2.5 rounded-xl border border-wb-line bg-wb-surface px-4";

  if (onSubmit) {
    return (
      <form onSubmit={onSubmit} className={className}>
        {input}
      </form>
    );
  }

  return <div className={className}>{input}</div>;
}
