"use client";

import { useState, useEffect } from "react";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";

type Props = {
  open: boolean;
  onClose: () => void;
  title: string;
  initialName?: string;
  initialSortOrder?: number;
  onSubmit: (categoryName: string, sortOrder: number) => Promise<void>;
};

export function CategoryFormModal({
  open,
  onClose,
  title,
  initialName = "",
  initialSortOrder = 0,
  onSubmit,
}: Props) {
  const [name, setName] = useState(initialName);
  const [sortOrder, setSortOrder] = useState(initialSortOrder.toString());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setName(initialName);
      setSortOrder(initialSortOrder.toString());
      setError(null);
    }
  }, [open, initialName, initialSortOrder]);

  const handleSubmit = async () => {
    if (!name.trim()) {
      setError("카테고리명을 입력해주세요.");
      return;
    }
    const parsedSortOrder = parseInt(sortOrder, 10);
    if (sortOrder.trim() === "" || isNaN(parsedSortOrder)) {
      setError("노출 순서는 올바른 숫자로 입력해주세요.");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      await onSubmit(name.trim(), parsedSortOrder);
      onClose();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  if (!open) return null;

  return (
    <Modal open={open} onClose={onClose} title={title}>
      <div className="flex flex-col gap-4">
        <TextField
          label="카테고리명"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="카테고리명을 입력하세요"
        />
        <TextField
          label="노출 순서"
          type="text"
          value={sortOrder}
          onChange={(e) => {
            const val = e.target.value;
            // 숫자만 허용 (빈 칸 포함)해서 백스페이스로 다 지울 수 있게 함
            if (val === "" || /^[0-9]+$/.test(val)) {
              setSortOrder(val);
            }
          }}
          placeholder="숫자 입력 (예: 1)"
        />
        {error && <p className="text-sm text-red-500">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            취소
          </Button>
          <Button onClick={handleSubmit} disabled={submitting}>
            {submitting ? "저장 중..." : "저장"}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
