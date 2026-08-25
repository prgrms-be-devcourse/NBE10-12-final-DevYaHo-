"use client";

import { notFound } from "next/navigation";
import { RequireAuth } from "@/components/auth/RequireAuth";
import { useAuth } from "@/lib/auth/AuthProvider";
import type { Role } from "@/lib/api/types";

function RoleCheck({ role, children }: { role: Role; children: React.ReactNode }) {
  const { member } = useAuth();

  if (member && member.role !== role) {
    notFound();
  }

  if (!member) {
    return (
      <div className="flex flex-1 items-center justify-center py-24 text-sm text-wb-secondary">
        불러오는 중...
      </div>
    );
  }

  return <>{children}</>;
}

export function RequireRole({ role, children }: { role: Role; children: React.ReactNode }) {
  return (
    <RequireAuth>
      <RoleCheck role={role}>{children}</RoleCheck>
    </RequireAuth>
  );
}
