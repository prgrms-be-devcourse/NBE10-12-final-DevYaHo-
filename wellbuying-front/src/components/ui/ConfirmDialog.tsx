"use client";

import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";

export function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  title,
  message,
  confirmLabel = "확인",
  destructive = false,
}: {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message: string;
  confirmLabel?: string;
  destructive?: boolean;
}) {
  return (
    <Modal open={open} onClose={onClose} title={title} width="380px">
      <p className="text-sm text-wb-secondary">{message}</p>
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          취소
        </Button>
        <Button
          onClick={() => {
            onConfirm();
            onClose();
          }}
          className={destructive ? "bg-red-600 hover:bg-red-600/90" : undefined}
        >
          {confirmLabel}
        </Button>
      </div>
    </Modal>
  );
}
