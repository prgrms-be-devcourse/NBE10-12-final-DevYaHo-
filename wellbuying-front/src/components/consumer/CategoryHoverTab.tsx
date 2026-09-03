"use client";

import { useState } from "react";
import { ChevronDown } from "lucide-react";

// "홈" 옆에 놓이는 카테고리 탭 - 마우스 포인터가 올라가 있는 동안만 드롭다운으로 카테고리 목록을 보여준다
export function CategoryHoverTab({
  categories,
  value,
  onChange,
}: {
  categories: string[];
  value: string;
  onChange: (category: string) => void;
}) {
  const [open, setOpen] = useState(false);

  return (
    <div className="relative" onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)}>
      <button
        type="button"
        className={`flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-sm font-semibold ${
          open ? "bg-wb-canvas text-wb-ink" : "text-wb-secondary hover:bg-wb-canvas hover:text-wb-ink"
        }`}
      >
        카테고리
        <ChevronDown className="h-3.5 w-3.5" />
      </button>

      {open && (
        <div className="absolute left-0 top-full z-20 w-40 space-y-0.5 rounded-xl border border-wb-line bg-wb-surface p-1.5 shadow-md">
          {categories.map((category) => (
            <button
              key={category}
              type="button"
              onClick={() => {
                onChange(category);
                setOpen(false);
              }}
              className={`block w-full rounded-lg px-3 py-2 text-left text-sm font-semibold ${
                value === category ? "bg-wb-green text-white" : "text-wb-ink hover:bg-wb-canvas"
              }`}
            >
              {category}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
