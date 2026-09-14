"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Bell } from "lucide-react";
import {
  getUnreadNotificationCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  subscribeNotificationStream,
} from "@/lib/api/notification";
import { getMyOrderIdByGroupBuy } from "@/lib/api/orders";
import type { NotificationResponse } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/AuthProvider";

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
    getUnreadNotificationCount()
      .then((response) => {
        if (!ignore) setUnreadCount(response.count);
      })
      .catch(() => {
        // 초기 조회가 실패해도 SSE로 새 알림이 오면 그때부터는 정확한 값으로 맞춰진다
      });

    // 30초 폴링 대신 SSE로 새 알림을 즉시 받는다 - 연결이 끊기면 subscribeNotificationStream이
    // 내부적으로 재연결을 시도한다
    const unsubscribe = subscribeNotificationStream((notification) => {
      if (ignore) return;
      setUnreadCount((count) => count + 1);
      setNotifications((items) => {
        if (items.some((item) => item.id === notification.id)) return items;
        return [notification, ...items];
      });
    });

    return () => {
      ignore = true;
      unsubscribe();
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

    // 결제 완료/실패 알림은 결제 시도 자체(주문)에 대한 알림이므로 마이페이지 > 결제 내역 > 해당 주문 상세로 보낸다.
    // 공동구매 성사/실패 알림은 결제가 시도되기 전이라 연결된 주문이 아직 없으므로 공동구매 상세로 보낸다
    if (notification.type === "PAYMENT_COMPLETED" || notification.type === "PAYMENT_FAILED") {
      try {
        const { orderId } = await getMyOrderIdByGroupBuy(notification.groupBuyId);
        router.push(`/orders?orderId=${encodeURIComponent(orderId)}`);
        return;
      } catch {
        // 주문을 못 찾으면(드묾) 결제 내역 목록으로라도 보낸다
        router.push("/orders");
        return;
      }
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
