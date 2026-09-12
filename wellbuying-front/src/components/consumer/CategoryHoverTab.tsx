"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
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

// "홈" 옆에 놓이는 카테고리 탭 - 마우스 포인터가 올라가 있는 동안만 카테고리 그리드를
// 아이콘과 함께 보여준다. 그리드는 탭 줄 아래 오버레이(absolute)로 펼쳐져서 바디 콘텐츠를
// 밀어내지 않으면서도, 그림자·카드 테두리 없이 헤더와 같은 배경으로 이어붙여 모달처럼 보이지 않게 한다.
// 카테고리 목록은 /api/categories에서 동적으로 받아온다.
// 타일을 고르면 페이지 내부 필터링 대신 /explore?category=이름 으로 이동한다.
export function CategoryHoverTab({ rightSlot }: { rightSlot?: React.ReactNode }) {
  const [open, setOpen] = useState(false);
  const [categoryNames, setCategoryNames] = useState<string[]>([]);
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const activeCategory = pathname === "/explore" ? (searchParams.get("category") ?? "전체") : null;

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
    setOpen(false);
    router.push(category === "전체" ? "/explore" : `/explore?category=${encodeURIComponent(category)}`);
  }

  return (
    <div className="relative" onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)}>
      <div className="flex items-center gap-6">
        <button
          type="button"
          className={`flex items-center gap-1.5 text-sm font-semibold ${
            open ? "text-wb-ink" : "text-wb-secondary hover:text-wb-ink"
          }`}
        >
          <LayoutGrid className="h-4 w-4" />
          카테고리
          <ChevronDown className="h-3.5 w-3.5" />
        </button>
        {rightSlot}
      </div>

      {open && (
        <div className="absolute left-1/2 top-[calc(100%+0.5rem)] z-20 w-screen -translate-x-1/2 bg-wb-surface">
          <div className="mx-auto max-w-6xl px-6">
            <div className="grid grid-cols-4 gap-x-10 gap-y-4 py-6">
              <CategoryTile
                label="전체"
                Icon={Grid3x3}
                active={activeCategory === "전체"}
                onClick={() => select("전체")}
              />
              {categoryNames.map((name) => (
                <CategoryTile
                  key={name}
                  label={name}
                  Icon={iconFor(name)}
                  active={activeCategory === name}
                  onClick={() => select(name)}
                />
              ))}
            </div>
          </div>
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
