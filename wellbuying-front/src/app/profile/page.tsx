"use client";

import { AccountShell } from "@/components/account/AccountShell";
import { ProfileContent } from "@/components/account/ProfileContent";

export default function ProfilePage() {
  return (
    <AccountShell>
      <ProfileContent />
    </AccountShell>
  );
}
