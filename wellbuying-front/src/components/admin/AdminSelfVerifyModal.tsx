"use client";

import { useState } from "react";
import { Banner } from "@/components/ui/Banner";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { useAuthStore } from "@/lib/auth/authStore";
import { showToast } from "@/lib/toast/toastStore";

// 공동구매 강제 정지 / 상품 강제 삭제처럼 파괴적인 관리자 작업 전에 쓰는 2차 인증 모달.
// 관리자가 자기 자신의 memberId를 다시 입력해 "본인이 맞는지" 재확인하는 개념이라, 다른 관리자
// 목록을 조회하지 않고 현재 로그인 세션(useAuthStore)의 memberId와 단순 비교만 한다.
// 일치하면 onVerified()를 호출하고 모달을 닫는다 - 이어서 사유 입력 모달(ActionReasonModal)을
// 띄우는 등 다음 단계는 호출 측이 결정한다. 불일치하면 토스트로 알리고 처리를 중단한다.
export function AdminSelfVerifyModal({
  open,
  title,
  onClose,
  onVerified,
}: {
  open: boolean;
  title: string;
  onClose: () => void;
  onVerified: () => void;
}) {
  const currentMemberId = useAuthStore((state) => state.member?.memberId);
  const [input, setInput] = useState("");
  const [error, setError] = useState<string | null>(null);

  function reset() {
    setInput("");
    setError(null);
  }

  function handleConfirm() {
    const inputId = Number(input.trim());
    if (!input.trim() || Number.isNaN(inputId) || inputId !== currentMemberId) {
      showToast("관리자 ID가 일치하지 않습니다", "error");
      reset();
      onClose();
      return;
    }
    reset();
    onVerified();
  }

  return (
    <Modal
      open={open}
      onClose={() => {
        reset();
        onClose();
      }}
      title={title}
      subtitle="본인 확인을 위해 관리자 ID(memberId)를 입력해주세요."
      width="380px"
    >
      <div className="space-y-4">
        <input
          type="number"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="관리자 ID"
          className="w-full rounded-lg border border-wb-line bg-wb-surface px-3 py-2 text-sm outline-none"
        />

        {error && <Banner tone="error">{error}</Banner>}

        <Button className="w-full" onClick={handleConfirm}>
          확인
        </Button>
      </div>
    </Modal>
  );
}
