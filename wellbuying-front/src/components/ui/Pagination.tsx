"use client";

import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react";

// 현재 페이지를 중심으로 최대 10개 숫자만 보여준다 (0-based page를 1-based로 표시)
const WINDOW_SIZE = 10;

function windowStartOf(page: number): number {
  return Math.floor(page / WINDOW_SIZE) * WINDOW_SIZE;
}

export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;

  const windowStart = windowStartOf(page);
  const windowEnd = Math.min(windowStart + WINDOW_SIZE, totalPages);
  const pages = Array.from({ length: windowEnd - windowStart }, (_, i) => windowStart + i);
  const hasPrevWindow = windowStart > 0;
  const hasNextWindow = windowEnd < totalPages;

  return (
    <nav className="flex items-center justify-center gap-1" aria-label="페이지네이션">
      <button
        type="button"
        disabled={!hasPrevWindow}
        onClick={() => onChange(windowStart - WINDOW_SIZE)}
        aria-label="이전 10페이지"
        className="flex h-8 w-8 items-center justify-center rounded-lg text-wb-secondary hover:bg-wb-canvas disabled:opacity-30 disabled:hover:bg-transparent"
      >
        <ChevronsLeft className="h-4 w-4" />
      </button>
      <button
        type="button"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
        aria-label="이전 페이지"
        className="flex h-8 w-8 items-center justify-center rounded-lg text-wb-secondary hover:bg-wb-canvas disabled:opacity-30 disabled:hover:bg-transparent"
      >
        <ChevronLeft className="h-4 w-4" />
      </button>

      {pages.map((p) => (
        <PageButton key={p} page={p} active={p === page} onClick={onChange} />
      ))}

      <button
        type="button"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
        aria-label="다음 페이지"
        className="flex h-8 w-8 items-center justify-center rounded-lg text-wb-secondary hover:bg-wb-canvas disabled:opacity-30 disabled:hover:bg-transparent"
      >
        <ChevronRight className="h-4 w-4" />
      </button>
      <button
        type="button"
        disabled={!hasNextWindow}
        onClick={() => onChange(windowStart + WINDOW_SIZE)}
        aria-label="다음 10페이지"
        className="flex h-8 w-8 items-center justify-center rounded-lg text-wb-secondary hover:bg-wb-canvas disabled:opacity-30 disabled:hover:bg-transparent"
      >
        <ChevronsRight className="h-4 w-4" />
      </button>
    </nav>
  );
}

function PageButton({ page, active, onClick }: { page: number; active: boolean; onClick: (page: number) => void }) {
  return (
    <button
      type="button"
      onClick={() => onClick(page)}
      aria-current={active ? "page" : undefined}
      className={`flex h-8 min-w-8 items-center justify-center rounded-lg px-2 text-xs font-bold transition-colors ${
        active ? "bg-wb-green text-white" : "text-wb-secondary hover:bg-wb-canvas"
      }`}
    >
      {page + 1}
    </button>
  );
}
