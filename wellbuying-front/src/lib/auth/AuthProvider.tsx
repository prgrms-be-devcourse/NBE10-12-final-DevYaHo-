"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { getMe } from "@/lib/api/auth";
import { getAccessToken } from "@/lib/auth/token-storage";
import type { MemberResponse } from "@/lib/api/types";

type AuthStatus = "loading" | "authenticated" | "unauthenticated";

type AuthContextValue = {
  status: AuthStatus;
  member: MemberResponse | null;
  refreshMember: () => Promise<void>;
  setMember: (member: MemberResponse | null) => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

// TEMP-DEV-STUB: remove before shipping — lets us render authenticated pages without a live backend.
const DEV_FAKE_ROLE: "BUYER" | "SELLER" | "ADMIN" | null = "ADMIN";

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [member, setMember] = useState<MemberResponse | null>(null);

  const refreshMember = useCallback(async () => {
    if (!getAccessToken()) {
      setMember(null);
      setStatus("unauthenticated");
      return;
    }
    try {
      const response = await getMe();
      setMember(response);
      setStatus("authenticated");
    } catch {
      setMember(null);
      setStatus("unauthenticated");
    }
  }, []);

  useEffect(() => {
    let ignore = false;

    async function loadInitialSession() {
      if (DEV_FAKE_ROLE) {
        setMember({ memberId: 1, email: "dev@wellbuying.kr", name: "테스트 계정", profileImageUrl: null, role: DEV_FAKE_ROLE });
        setStatus("authenticated");
        return;
      }
      if (!getAccessToken()) {
        if (!ignore) setStatus("unauthenticated");
        return;
      }
      try {
        const response = await getMe();
        if (!ignore) {
          setMember(response);
          setStatus("authenticated");
        }
      } catch {
        if (!ignore) {
          setMember(null);
          setStatus("unauthenticated");
        }
      }
    }

    loadInitialSession();
    return () => {
      ignore = true;
    };
  }, []);

  return (
    <AuthContext.Provider value={{ status, member, refreshMember, setMember }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
}
