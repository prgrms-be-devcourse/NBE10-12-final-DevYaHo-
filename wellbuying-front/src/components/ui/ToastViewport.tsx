"use client";

import { CheckCircle2, XCircle } from "lucide-react";
import { useToastStore } from "@/lib/toast/toastStore";

const TONE_STYLE = {
  success: "bg-wb-light-green/60 text-wb-green border-wb-green/30",
  error: "bg-red-50 text-red-700 border-red-200",
};

// 승인/반려 등 관리자 액션 결과를 알려주는 토스트를 화면 우하단에 쌓아서 보여준다.
// AppShell 등 레이아웃 최상단에 한 번만 마운트한다.
export function ToastViewport() {
  const toasts = useToastStore((state) => state.toasts);
  const dismiss = useToastStore((state) => state.dismiss);

  if (toasts.length === 0) return null;

  return (
    <div className="fixed bottom-6 right-6 z-50 flex flex-col gap-2">
      {toasts.map((toast) => {
        const Icon = toast.tone === "success" ? CheckCircle2 : XCircle;
        return (
          <div
            key={toast.id}
            role="status"
            className={`flex items-center gap-2 rounded-lg border px-4 py-3 text-sm font-semibold shadow-lg ${TONE_STYLE[toast.tone]}`}
          >
            <Icon className="h-4 w-4 shrink-0" />
            <span>{toast.message}</span>
            <button
              onClick={() => dismiss(toast.id)}
              className="ml-2 text-xs font-bold opacity-60 hover:opacity-100"
              aria-label="닫기"
            >
              ✕
            </button>
          </div>
        );
      })}
    </div>
  );
}
