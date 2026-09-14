"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { SearchBar } from "@/components/consumer/SearchBar";
import { autocompleteProducts } from "@/lib/api/product";
import type { ProductAutocompleteResponse } from "@/lib/api/types";

// 헤더(로고 옆)에 상시 노출되는 검색바 - 어느 화면에서 입력해도 항상 /explore로 이동해 결과를 보여준다
export function HeaderSearchBar() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [value, setValue] = useState(() => searchParams.get("q") ?? "");
  const [suggestions, setSuggestions] = useState<ProductAutocompleteResponse[]>([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setValue(searchParams.get("q") ?? "");
  }, [searchParams]);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    const trimmed = value.trim();
    if (trimmed.length === 0) {
      setSuggestions([]);
      return;
    }
    debounceRef.current = setTimeout(() => {
      autocompleteProducts(trimmed)
        .then(setSuggestions)
        .catch(() => setSuggestions([]));
    }, 300);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [value]);

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setShowDropdown(false);
      }
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, []);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = value.trim();
    setShowDropdown(false);
    router.push(trimmed.length > 0 ? `/explore?q=${encodeURIComponent(trimmed)}` : "/explore");
  }

  function handleSelect(name: string) {
    setValue(name);
    setSuggestions([]);
    setShowDropdown(false);
    router.push(`/explore?q=${encodeURIComponent(name)}`);
  }

  return (
    <div ref={containerRef} className="relative">
      <SearchBar
        size="sm"
        value={value}
        onChange={(v) => {
          setValue(v);
          setShowDropdown(true);
        }}
        onSubmit={handleSubmit}
      />
      {showDropdown && suggestions.length > 0 && (
        <ul className="absolute left-0 top-full z-50 mt-1 w-full overflow-hidden rounded-lg border border-wb-line bg-white shadow-lg">
          {suggestions.map((s) => (
            <li key={s.id}>
              <button
                type="button"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => handleSelect(s.productName)}
                className="w-full px-4 py-2.5 text-left text-sm hover:bg-wb-canvas"
              >
                {s.productName}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
