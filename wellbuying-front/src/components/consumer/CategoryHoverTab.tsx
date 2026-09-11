"use client";

import { useEffect, useState } from "react";
import {
  ChevronDown,
  Grid3x3,
  Home,
  LayoutGrid,
  Sparkles,
  Tag,
  Utensils,
  type LucideIcon,
} from "lucide-react";
import { listCategories } from "@/lib/api/category";

// 최상위 카테고리명별 아이콘 - 매핑에 없는 카테고리는 Tag로 대체
const CATEGORY_ICONS: Record<string, LucideIcon> = {
  식품: Utensils,
  생활용품: Home,
  뷰티: Sparkles,
};

function iconFor(categoryName: string): LucideIcon {
  return CATEGORY_ICONS[categoryName] ?? Tag;
}

// "홈" 옆에 놓이는 카테고리 탭 - 마우스 포인터가 올라가 있는 동안만 드롭다운으로 최상위 카테고리를
// 아이콘과 함께 그리드로 보여준다. 카테고리 목록은 /api/categories에서 동적으로 받아온다.
export function CategoryHoverTab({ value, onChange }: { value: string; onChange: (category: string) => void }) {
  const [open, setOpen] = useState(false);
  const [categoryNames, setCategoryNames] = useState<string[]>([]);

  useEffect(() => {
    let ignore = false;
    listCategories()
      .then((tree) => {
        if (!ignore) setCategoryNames(tree.map((node) => node.categoryName));
      })
      .catch(() => {
        if (!ignore) setCategoryNames([]);
      });
    return () => {
      ignore = true;
    };
  }, []);

  function select(category: string) {
    onChange(category);
    setOpen(false);
  }

  return (
    <div className="relative" onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)}>
      <button
        type="button"
        className={`flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-sm font-semibold ${
          open ? "bg-wb-canvas text-wb-ink" : "text-wb-secondary hover:bg-wb-canvas hover:text-wb-ink"
        }`}
      >
        <LayoutGrid className="h-3.5 w-3.5" />
        카테고리
        <ChevronDown className="h-3.5 w-3.5" />
      </button>

      {open && (
        <div className="absolute left-0 top-full z-20 grid w-[360px] grid-cols-2 gap-x-4 gap-y-1 rounded-xl border border-wb-line bg-wb-surface p-3 shadow-md sm:grid-cols-3">
          <CategoryTile label="전체" Icon={Grid3x3} active={value === "전체"} onClick={() => select("전체")} />
          {categoryNames.map((name) => (
            <CategoryTile key={name} label={name} Icon={iconFor(name)} active={value === name} onClick={() => select(name)} />
          ))}
        </div>
      )}
    </div>
  );
}

function CategoryTile({
  label,
  Icon,
  active,
  onClick,
}: {
  label: string;
  Icon: LucideIcon;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex items-center gap-2 rounded-lg px-2 py-2 text-left text-sm font-semibold ${
        active ? "bg-wb-green text-white" : "text-wb-ink hover:bg-wb-canvas"
      }`}
    >
      <Icon className="h-4 w-4 shrink-0" strokeWidth={2} />
      <span className="truncate">{label}</span>
    </button>
  );
}
