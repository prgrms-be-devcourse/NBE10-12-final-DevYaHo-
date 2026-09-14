"use client";

import { useState } from "react";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { deleteProduct } from "@/lib/api/product";
import { ApiError } from "@/lib/api/http";
import type { ProductMineResponse } from "@/lib/api/types";

export function ProductDeleteModal({
  open,
  product,
  onClose,
  onDeleted,
}: {
  open: boolean;
  product: ProductMineResponse | null;
  onClose: () => void;
  onDeleted: (productId: number) => void;
}) {
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function handleClose() {
    setReason("");
    setError(null);
    onClose();
  }

  async function handleSubmit() {
    if (!product) return;
    setError(null);
    if (!reason.trim()) {
      setError("삭제 사유를 입력해주세요.");
      return;
    }
    setSubmitting(true);
    try {
      await deleteProduct(product.id, { reason });
      setReason("");
      onDeleted(product.id);
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "상품 삭제 중 오류가 발생했어요.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title="상품 삭제"
      subtitle={product ? `"${product.productName}" 상품을 삭제합니다.` : ""}
      width="420px"
    >
      <div className="space-y-4">
        <p className="text-sm text-wb-secondary">
          진행 중인 공동구매가 있으면 삭제할 수 없어요. 삭제 사유를 입력해주세요.
        </p>

        <TextField
          label="삭제 사유 (최대 500자)"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          maxLength={500}
        />

        {error && <Banner tone="error">{error}</Banner>}

        <div className="flex gap-2">
          <Button variant="secondary" className="flex-1" onClick={handleClose}>
            취소
          </Button>
          <Button
            className="flex-1 bg-red-600 hover:bg-red-600/90"
            loading={submitting}
            onClick={handleSubmit}
          >
            삭제
          </Button>
        </div>
      </div>
    </Modal>
  );
}