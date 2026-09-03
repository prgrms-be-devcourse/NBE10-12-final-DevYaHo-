"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { SearchBar } from "@/components/consumer/SearchBar";

// 헤더(로고 옆)에 상시 노출되는 검색바 - 어느 화면에서 입력해도 항상 /explore로 이동해 결과를 보여준다
export function HeaderSearchBar() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [value, setValue] = useState(() => searchParams.get("q") ?? "");

  useEffect(() => {
    setValue(searchParams.get("q") ?? "");
  }, [searchParams]);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = value.trim();
    router.push(trimmed.length > 0 ? `/explore?q=${encodeURIComponent(trimmed)}` : "/explore");
  }

  return <SearchBar size="sm" value={value} onChange={setValue} onSubmit={handleSubmit} />;
}
