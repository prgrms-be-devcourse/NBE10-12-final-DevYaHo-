"use client";

import { useState } from "react";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { requestGroupBuySuspension } from "@/lib/api/groupBuy";
import { ApiError } from "@/lib/api/http";

export function SuspensionRequestModal({
  open,
  groupBuyId,
  onClose,
  onRequested,
}: {
  open: boolean;
  groupBuyId: number | null;
  onClose: () => void;
  onRequested: () => void;
}) {
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function reset() {
    setReason("");
    setError(null);
  }

  async function handleSubmit() {
    if (groupBuyId === null) return;
    setError(null);
    setSubmitting(true);
    try {
      await requestGroupBuySuspension(groupBuyId, { reason: reason.trim() || undefined });
      reset();
      onRequested();
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "판매정지 요청 중 오류가 발생했어요.");
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
      title="판매정지 요청"
      subtitle="관리자가 검토 후 승인하면 신규 참여가 막혀요. 기존 참여 내역은 유지됩니다."
      width="420px"
    >
      <div className="space-y-4">
        <div>
          <span className="mb-1 block text-xs font-bold">요청 사유 (선택)</span>
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={3}
            className="w-full resize-none rounded-lg border border-wb-line bg-wb-surface px-3 py-2 text-sm outline-none"
          />
        </div>

        {error && <Banner tone="error">{error}</Banner>}

        <Button className="w-full" loading={submitting} onClick={handleSubmit}>
          판매정지 요청
        </Button>
      </div>
    </Modal>
  );
}
