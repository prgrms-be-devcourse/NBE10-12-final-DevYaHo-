"use client";

import { useEffect, useRef, useState } from "react";
import { Image as ImageIcon } from "lucide-react";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { SelectField } from "@/components/ui/SelectField";
import { TextField } from "@/components/ui/TextField";
import { listCategories } from "@/lib/api/category";
import { updateProduct, requestProductThumbnailUploadUrl } from "@/lib/api/product";
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
  const [startPrice, setStartPrice] = useState<number | "">(10_000);
  const [thumbnailUrl, setThumbnailUrl] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string>("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const selectedParent = categoryTree.find((c) => c.id === parentCategoryId) ?? null;
  const subCategories = selectedParent?.children ?? [];

  useEffect(() => {
    if (file) {
      const url = URL.createObjectURL(file);
      // eslint-disable-next-line react-hooks/set-state-in-effect -- URL.createObjectURL은 브라우저 리소스라 cleanup(revokeObjectURL)이 필수 - 렌더링 중에는 만들 수 없는 부수효과
      setPreviewUrl(url);
      return () => URL.revokeObjectURL(url);
    }
  }, [file]);

  useEffect(() => {
    if (!open || !product) return;
    let ignore = false;

    // eslint-disable-next-line react-hooks/set-state-in-effect -- 모달 대상 상품이 바뀔 때 폼 초기화 + 관련 데이터 재조회
    setProductName(product.productName);
    setStartPrice(product.startPrice);
    setThumbnailUrl(product.thumbnailUrl ?? "");
    setPreviewUrl(product.thumbnailUrl ?? "");
    setFile(null);
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
    if (startPrice === "" || startPrice < 0) {
      setError("판매가는 0원 이상으로 입력해주세요.");
      return;
    }
    setSubmitting(true);
    try {
      let finalThumbnailUrl = thumbnailUrl;
      if (file) {
        const { uploadUrl, thumbnailUrl: uploadedUrl } = await requestProductThumbnailUploadUrl(file.type);
        const uploadRes = await fetch(uploadUrl, {
          method: "PUT",
          body: file,
          headers: {
            "Content-Type": file.type,
            "x-amz-tagging": "pending=true",
          },
        });
        if (!uploadRes.ok) throw new Error("이미지 업로드에 실패했어요.");
        finalThumbnailUrl = uploadedUrl;
      }
      await updateProduct(product.id, {
        categoryId: finalCategoryId,
        productName,
        description: description.trim() || undefined,
        startPrice,
        thumbnailUrl: finalThumbnailUrl.trim() || undefined,
      });
      onUpdated();
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : "상품 수정 중 오류가 발생했어요.");
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
              <SelectField
                className="flex-1"
                placeholder="카테고리 선택"
                value={parentCategoryId ? String(parentCategoryId) : ""}
                onChange={(value) => handleParentChange(Number(value))}
                options={categoryTree.map((category) => ({
                  value: String(category.id),
                  label: category.categoryName,
                }))}
              />
              {subCategories.length > 0 && (
                <SelectField
                  className="flex-1"
                  placeholder="하위 카테고리 선택"
                  value={subCategoryId ? String(subCategoryId) : ""}
                  onChange={(value) => setSubCategoryId(Number(value))}
                  options={subCategories.map((sub) => ({
                    value: String(sub.id),
                    label: sub.categoryName,
                  }))}
                />
              )}
            </div>
          )}
        </div>

        <TextField
          label="판매가(원)"
          inputMode="numeric"
          value={startPrice === "" ? "" : startPrice.toLocaleString("ko-KR")}
          onChange={(e) => {
            const digitsOnly = e.target.value.replace(/[^0-9]/g, "");
            setStartPrice(digitsOnly === "" ? "" : Number(digitsOnly));
          }}
        />

        <div>
          <span className="mb-1 block text-xs font-bold">상품 이미지 (선택)</span>
          <div className="flex items-center gap-3">
            {previewUrl ? (
              /* eslint-disable-next-line @next/next/no-img-element */
              <img
                src={previewUrl}
                alt="상품 이미지 미리보기"
                className="h-20 w-20 rounded-lg border border-wb-line object-cover"
              />
            ) : (
              <div className="flex h-20 w-20 items-center justify-center rounded-lg border border-wb-line bg-wb-canvas text-wb-secondary">
                <ImageIcon className="h-8 w-8" />
              </div>
            )}
            <input
              type="file"
              accept="image/jpeg, image/png, image/webp"
              className="hidden"
              ref={fileInputRef}
              onChange={(e) => setFile(e.target.files?.[0] || null)}
            />
            <Button
              type="button"
              variant="secondary"
              className="px-3 py-1 text-xs"
              onClick={() => fileInputRef.current?.click()}
            >
              이미지 선택
            </Button>
          </div>
        </div>

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
