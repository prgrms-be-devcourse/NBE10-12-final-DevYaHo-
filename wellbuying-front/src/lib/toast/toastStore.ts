import { create } from "zustand";

export type ToastTone = "success" | "error";

export type Toast = {
  id: number;
  message: string;
  tone: ToastTone;
};

type ToastState = {
  toasts: Toast[];
  dismiss: (id: number) => void;
};

let nextId = 0;

const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  dismiss: (id) => set((state) => ({ toasts: state.toasts.filter((toast) => toast.id !== id) })),
}));

// 훅 규칙에 얽매이지 않고 이벤트 핸들러 어디서든 바로 호출할 수 있도록 함수 형태로 노출한다
export function showToast(message: string, tone: ToastTone = "success") {
  const id = nextId++;
  useToastStore.setState((state) => ({ toasts: [...state.toasts, { id, message, tone }] }));
  setTimeout(() => useToastStore.getState().dismiss(id), 3000);
}

export { useToastStore };
