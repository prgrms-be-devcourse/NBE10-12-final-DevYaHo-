"use client";

import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react";

const BLOCK_SIZE = 10;

// page/onChange는 0-베이스(백엔드 Pageable 규약). 화면에는 1-베이스 번호로 표시한다.
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

  const blockStart = Math.floor(page / BLOCK_SIZE) * BLOCK_SIZE;
  const blockEnd = Math.min(blockStart + BLOCK_SIZE, totalPages);
  const pageNumbers = Array.from({ length: blockEnd - blockStart }, (_, i) => blockStart + i);
  const hasPrevBlock = blockStart > 0;
  const hasNextBlock = blockEnd < totalPages;

  return (
    <div className="flex items-center justify-center gap-1">
      <PageButton disabled={!hasPrevBlock} onClick={() => onChange(blockStart - BLOCK_SIZE)} aria-label="이전 10페이지">
        <ChevronsLeft className="h-4 w-4" />
      </PageButton>
      <PageButton disabled={page === 0} onClick={() => onChange(page - 1)} aria-label="이전 페이지">
        <ChevronLeft className="h-4 w-4" />
      </PageButton>
      {pageNumbers.map((p) => (
        <PageButton key={p} active={p === page} onClick={() => onChange(p)}>
          {p + 1}
        </PageButton>
      ))}
      <PageButton disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)} aria-label="다음 페이지">
        <ChevronRight className="h-4 w-4" />
      </PageButton>
      <PageButton disabled={!hasNextBlock} onClick={() => onChange(blockEnd)} aria-label="다음 10페이지">
        <ChevronsRight className="h-4 w-4" />
      </PageButton>
    </div>
  );
}

function PageButton({
  children,
  active,
  disabled,
  onClick,
  "aria-label": ariaLabel,
}: {
  children: React.ReactNode;
  active?: boolean;
  disabled?: boolean;
  onClick: () => void;
  "aria-label"?: string;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      aria-label={ariaLabel}
      aria-current={active ? "page" : undefined}
      className={`flex h-8 min-w-8 items-center justify-center rounded-lg px-2 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-40 ${
        active ? "bg-wb-green text-white" : "text-wb-ink hover:bg-wb-canvas"
      }`}
    >
      {children}
    </button>
  );
}
