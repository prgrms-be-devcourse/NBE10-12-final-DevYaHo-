import { create } from "zustand";
import { getMe } from "@/lib/api/auth";
import { getAccessToken } from "@/lib/auth/token-storage";
import type { MemberResponse } from "@/lib/api/types";

export type AuthStatus = "loading" | "authenticated" | "unauthenticated";

type AuthState = {
  status: AuthStatus;
  member: MemberResponse | null;
  setMember: (member: MemberResponse | null) => void;
  refreshMember: () => Promise<void>;
  loadInitialSession: () => Promise<void>;
};

export const useAuthStore = create<AuthState>((set) => ({
  status: "loading",
  member: null,

  setMember: (member) => set({ member }),

  refreshMember: async () => {
    if (!getAccessToken()) {
      set({ member: null, status: "unauthenticated" });
      return;
    }
    try {
      const response = await getMe();
      set({ member: response, status: "authenticated" });
    } catch {
      set({ member: null, status: "unauthenticated" });
    }
  },

  loadInitialSession: async () => {
    if (!getAccessToken()) {
      set({ status: "unauthenticated" });
      return;
    }
    try {
      const response = await getMe();
      set({ member: response, status: "authenticated" });
    } catch {
      set({ member: null, status: "unauthenticated" });
    }
  },
}));
