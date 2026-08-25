"use client";

import { useEffect, useRef } from "react";
import { useShallow } from "zustand/react/shallow";
import { useAuthStore } from "@/lib/auth/authStore";

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const loadInitialSession = useAuthStore((state) => state.loadInitialSession);
  const initialized = useRef(false);

  useEffect(() => {
    if (initialized.current) return;
    initialized.current = true;
    loadInitialSession();
  }, [loadInitialSession]);

  return children;
}

export function useAuth() {
  return useAuthStore(
    useShallow((state) => ({
      status: state.status,
      member: state.member,
      refreshMember: state.refreshMember,
      setMember: state.setMember,
    })),
  );
}
