"use client";

import { useEffect, useState } from "react";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { listCategories } from "@/lib/api/category";
import { updateProduct } from "@/lib/api/product";
import { ApiError } from "@/lib/api/http";
import type { CategoryTreeResponse, ProductMineResponse } from "@/lib/api/types";

function resolveCategory(tree: CategoryTreeResponse[], categoryId: number): { parentId: number; subId: number | null } {
  for (const parent of tree) {
    if (parent.id === categoryId) return { parentId: parent.id, subId: null };
    for (const child of parent.children) {
      if (child.id === categoryId) return { parentId: parent.id, subId: child.id };
    }
  }
  return { parentId: categoryId, subId: null };
}

export function ProductEditModal({
  open,
  product,
  onClose,
  onUpdated,
}: {
  open: boolean;
  product: ProductMineResponse | null;
  onClose: () => void;
  onUpdated: () => void;
}) {
  const [categoryTree, setCategoryTree] = useState<CategoryTreeResponse[]>([]);
  const [categoriesLoading, setCategoriesLoading] = useState(true);
  const [parentCategoryId, setParentCategoryId] = useState<number | null>(null);
  const [subCategoryId, setSubCategoryId] = useState<number | null>(null);
  const [productName, setProductName] = useState("");
  const [description, setDescription] = useState("");
  const [startPrice, setStartPrice] = useState(10_000);
  const [thumbnailUrl, setThumbnailUrl] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedParent = categoryTree.find((c) => c.id === parentCategoryId) ?? null;
  const subCategories = selectedParent?.children ?? [];

  useEffect(() => {
    if (!open || !product) return;
    let ignore = false;

    setProductName(product.productName);
    setStartPrice(product.startPrice);
    setThumbnailUrl(product.thumbnailUrl ?? "");
    setDescription(product.description ?? "");
    setError(null);

    async function loadCategories() {
      setCategoriesLoading(true);
      try {
        const tree = await listCategories();
        if (!ignore) {
          setCategoryTree(tree);
          const { parentId, subId } = resolveCategory(tree, product!.categoryId);
          setParentCategoryId(parentId);
          setSubCategoryId(subId);
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
  }, [open, product]);

  function handleParentChange(id: number) {
    setParentCategoryId(id);
    setSubCategoryId(null);
  }

  async function handleSubmit() {
    if (!product) return;
    setError(null);
    if (!productName.trim()) {
      setError("상품명을 입력해주세요.");
      return;
    }
    if (!parentCategoryId) {
      setError("카테고리를 선택해주세요.");
      return;
    }
    if (subCategories.length > 0 && !subCategoryId) {
      setError("하위 카테고리를 선택해주세요.");
      return;
    }
    const finalCategoryId = subCategoryId ?? parentCategoryId;
    if (!Number.isFinite(startPrice) || startPrice < 0) {
      setError("판매가는 0원 이상으로 입력해주세요.");
      return;
    }
    setSubmitting(true);
    try {
      await updateProduct(product.id, {
        categoryId: finalCategoryId,
        productName,
        description: description.trim() || undefined,
        startPrice,
        thumbnailUrl: thumbnailUrl.trim() || undefined,
      });
      onUpdated();
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "상품 수정 중 오류가 발생했어요.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="상품 수정"
      subtitle="수정할 상품 정보를 입력해주세요."
      width="480px"
    >
      <div className="space-y-4">
        <TextField label="상품명" value={productName} onChange={(e) => setProductName(e.target.value)} />

        <div>
          <span className="mb-1 block text-xs font-bold">카테고리</span>
          {categoriesLoading ? (
            <p className="text-sm text-wb-secondary">불러오는 중...</p>
          ) : categoryTree.length === 0 ? (
            <p className="text-sm text-wb-secondary">등록된 카테고리가 없어요.</p>
          ) : (
            <div className="flex gap-2">
              <select
                value={parentCategoryId ?? ""}
                onChange={(e) => handleParentChange(Number(e.target.value))}
                className="h-11 flex-1 rounded-lg border border-wb-line bg-wb-surface px-3 text-sm font-semibold outline-none"
              >
                {categoryTree.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.categoryName}
                  </option>
                ))}
              </select>
              {subCategories.length > 0 && (
                <select
                  value={subCategoryId ?? ""}
                  onChange={(e) => setSubCategoryId(Number(e.target.value))}
                  className="h-11 flex-1 rounded-lg border border-wb-line bg-wb-surface px-3 text-sm font-semibold outline-none"
                >
                  {subCategories.map((sub) => (
                    <option key={sub.id} value={sub.id}>
                      {sub.categoryName}
                    </option>
                  ))}
                </select>
              )}
            </div>
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
          disabled={
            (!categoriesLoading && categoryTree.length === 0) ||
            !parentCategoryId ||
            (subCategories.length > 0 && !subCategoryId)
          }
          onClick={handleSubmit}
        >
          수정 완료
        </Button>
      </div>
    </Modal>
  );
}
