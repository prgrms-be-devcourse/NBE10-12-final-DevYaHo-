"use client";

import { useState } from "react";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { ApiError } from "@/lib/api/http";

// 백엔드 AdminActionReasonRequest가 reason(minLen 1)을 필수로 요구한다
export function ActionReasonModal({
  open,
  title,
  actionLabel,
  confirmVariant = "primary",
  onClose,
  onConfirm,
}: {
  open: boolean;
  title: string;
  actionLabel: string;
  confirmVariant?: "primary" | "secondary";
  onClose: () => void;
  onConfirm: (reason: string) => Promise<void>;
}) {
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function reset() {
    setReason("");
    setError(null);
  }

  async function handleSubmit() {
    if (reason.trim().length === 0) {
      setError("사유를 입력해주세요.");
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await onConfirm(reason.trim());
      reset();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "처리 중 오류가 발생했어요.");
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
      title={title}
      subtitle="처리 사유를 입력해주세요."
      width="420px"
    >
      <div className="space-y-4">
        <textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          placeholder="사유를 입력해주세요."
          className="w-full resize-none rounded-lg border border-wb-line bg-wb-surface px-3 py-2 text-sm outline-none"
        />

        {error && <Banner tone="error">{error}</Banner>}

        <Button className="w-full" variant={confirmVariant} loading={submitting} onClick={handleSubmit}>
          {actionLabel}
        </Button>
      </div>
    </Modal>
  );
}
