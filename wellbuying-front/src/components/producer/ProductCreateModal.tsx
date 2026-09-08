"use client";

import { useEffect, useState } from "react";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { listCategories } from "@/lib/api/category";
import { createProduct } from "@/lib/api/product";
import { ApiError } from "@/lib/api/http";
import type { CategoryTreeResponse } from "@/lib/api/types";

// 지금은 최상위 카테고리만 선택할 수 있게 한다(백엔드 시드도 최상위만 존재).
// 나중에 2단계 연동 드롭다운으로 확장할 때는 선택된 최상위의 children으로
// 두 번째 select를 그리면 된다 - 그래서 응답 트리 전체를 state에 그대로 들고 있는다.
function toTopLevelOptions(tree: CategoryTreeResponse[]): { id: number; label: string }[] {
  return tree.map((category) => ({ id: category.id, label: category.categoryName }));
}

export function ProductCreateModal({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [categoryTree, setCategoryTree] = useState<CategoryTreeResponse[]>([]);
  const [categoriesLoading, setCategoriesLoading] = useState(true);
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [productName, setProductName] = useState("");
  const [description, setDescription] = useState("");
  const [startPrice, setStartPrice] = useState(10_000);
  const [thumbnailUrl, setThumbnailUrl] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 최상위 카테고리만 select 옵션으로 노출 (2단계 확장 시 여기서 하위 select 추가)
  const categories = toTopLevelOptions(categoryTree);

  useEffect(() => {
    if (!open) return;
    let ignore = false;

    async function loadCategories() {
      setCategoriesLoading(true);
      try {
        const tree = await listCategories();
        if (!ignore) {
          setCategoryTree(tree);
          setCategoryId((current) => current ?? tree[0]?.id ?? null);
        }
      } catch {
        if (!ignore) setCategoryTree([]);
      } finally {
        if (!ignore) setCategoriesLoading(false);
      }
    }

    loadCategories();
    return () => {
      ignore = true;
    };
  }, [open]);

  function reset() {
    setProductName("");
    setCategoryId(categories[0]?.id ?? null);
    setDescription("");
    setStartPrice(10_000);
    setThumbnailUrl("");
    setError(null);
  }

  async function handleSubmit() {
    setError(null);
    if (!productName.trim()) {
      setError("상품명을 입력해주세요.");
      return;
    }
    if (!categoryId) {
      setError("카테고리를 선택해주세요.");
      return;
    }
    if (!Number.isFinite(startPrice) || startPrice <= 0) {
      setError("판매가는 0원보다 크게 입력해주세요.");
      return;
    }
    setSubmitting(true);
    try {
      await createProduct({
        categoryId,
        productName,
        description: description.trim() || undefined,
        startPrice,
        thumbnailUrl: thumbnailUrl.trim() || undefined,
      });
      reset();
      onCreated();
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "상품 등록 중 오류가 발생했어요.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={() => {
        reset();
        onClose();
      }}
      title="상품 등록"
      subtitle="공동구매를 열 상품 정보를 입력해주세요."
      width="480px"
    >
      <div className="space-y-4">
        <TextField label="상품명" value={productName} onChange={(e) => setProductName(e.target.value)} />

        <div>
          <span className="mb-1 block text-xs font-bold">카테고리</span>
          {categoriesLoading ? (
            <p className="text-sm text-wb-secondary">불러오는 중...</p>
          ) : categories.length === 0 ? (
            <p className="text-sm text-wb-secondary">등록된 카테고리가 없어요.</p>
          ) : (
            <select
              value={categoryId ?? ""}
              onChange={(e) => setCategoryId(Number(e.target.value))}
              className="h-11 w-full rounded-lg border border-wb-line bg-wb-surface px-3 text-sm font-semibold outline-none"
            >
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.label}
                </option>
              ))}
            </select>
          )}
        </div>

        <TextField
          label="판매가(원)"
          type="number"
          min={0}
          value={startPrice}
          onChange={(e) => setStartPrice(Number(e.target.value))}
        />

        <TextField
          label="썸네일 URL (선택)"
          value={thumbnailUrl}
          onChange={(e) => setThumbnailUrl(e.target.value)}
        />

        <div>
          <span className="mb-1 block text-xs font-bold">상품 설명 (선택)</span>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            className="w-full resize-none rounded-lg border border-wb-line bg-wb-surface px-3 py-2 text-sm outline-none"
          />
        </div>

        {error && <Banner tone="error">{error}</Banner>}

        <Button
          className="w-full"
          loading={submitting}
          disabled={!categoriesLoading && categories.length === 0}
          onClick={handleSubmit}
        >
          상품 등록
        </Button>
      </div>
    </Modal>
  );
}
