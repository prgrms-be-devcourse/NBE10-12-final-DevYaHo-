"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Bell } from "lucide-react";
import {
  getUnreadNotificationCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from "@/lib/api/notification";
import type { NotificationResponse } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/AuthProvider";

const POLL_INTERVAL_MS = 30_000;

function formatCreatedAt(createdAt: string): string {
  return new Date(createdAt).toLocaleString("ko-KR", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function NotificationBell() {
  const { member } = useAuth();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!member) return;

    let ignore = false;
    function refreshUnreadCount() {
      getUnreadNotificationCount()
        .then((response) => {
          if (!ignore) setUnreadCount(response.count);
        })
        .catch(() => {
          // 폴링 실패는 다음 주기에 재시도하면 되므로 조용히 무시
        });
    }

    refreshUnreadCount();
    const timer = setInterval(refreshUnreadCount, POLL_INTERVAL_MS);
    return () => {
      ignore = true;
      clearInterval(timer);
    };
  }, [member]);

  function toggleOpen() {
    const next = !open;
    setOpen(next);
    if (next) {
      setLoading(true);
      listNotifications({ size: 20 })
        .then((response) => setNotifications(response.content))
        .catch(() => setNotifications([]))
        .finally(() => setLoading(false));
    }
  }

  async function handleItemClick(notification: NotificationResponse) {
    setOpen(false);
    if (!notification.read) {
      setUnreadCount((count) => Math.max(0, count - 1));
      markNotificationRead(notification.id).catch(() => {
        // 읽음 처리 실패해도 화면 이동은 그대로 진행
      });
    }
    router.push(`/deals/${notification.groupBuyId}`);
  }

  async function handleMarkAllRead() {
    setUnreadCount(0);
    setNotifications((items) => items.map((item) => ({ ...item, read: true })));
    try {
      await markAllNotificationsRead();
    } catch {
      // 실패해도 다음 폴링에서 실제 상태로 다시 맞춰진다
    }
  }

  if (!member) return null;

  return (
    <div className="relative flex items-center">
      <button
        onClick={toggleOpen}
        aria-label="알림 열기"
        className="relative flex items-center justify-center rounded-lg p-2 hover:bg-wb-canvas"
      >
        <Bell className="h-4 w-4 text-wb-ink" strokeWidth={2} />
        {unreadCount > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-wb-orange px-1 text-[10px] font-bold text-white">
            {unreadCount > 99 ? "99+" : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <>
          <button aria-label="닫기" onClick={() => setOpen(false)} className="fixed inset-0 z-10 cursor-default" />
          <div className="absolute right-0 top-full z-20 mt-1 w-80 rounded-xl border border-wb-line bg-wb-surface shadow-md">
            <div className="flex items-center justify-between border-b border-wb-line px-3 py-2">
              <span className="text-sm font-bold text-wb-ink">알림</span>
              <button
                onClick={handleMarkAllRead}
                className="text-xs font-semibold text-wb-secondary hover:text-wb-ink"
              >
                모두 읽음
              </button>
            </div>
            <div className="max-h-96 overflow-y-auto">
              {loading && <p className="px-3 py-6 text-center text-xs text-wb-secondary">불러오는 중...</p>}
              {!loading && notifications.length === 0 && (
                <p className="px-3 py-6 text-center text-xs text-wb-secondary">알림이 없습니다.</p>
              )}
              {!loading &&
                notifications.map((notification) => (
                  <button
                    key={notification.id}
                    onClick={() => handleItemClick(notification)}
                    className={`flex w-full flex-col items-start gap-0.5 border-b border-wb-line px-3 py-2.5 text-left last:border-b-0 hover:bg-wb-canvas ${
                      notification.read ? "" : "bg-wb-orange/5"
                    }`}
                  >
                    <p className="text-xs font-semibold text-wb-ink">{notification.message}</p>
                    <p className="text-[11px] text-wb-secondary">{formatCreatedAt(notification.createdAt)}</p>
                  </button>
                ))}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
