import { http } from "@/lib/api/http";
import type { NotificationResponse, NotificationUnreadCountResponse, PageResponse } from "@/lib/api/types";

export function listNotifications(params?: { page?: number; size?: number }): Promise<PageResponse<NotificationResponse>> {
  const query = new URLSearchParams();
  if (params?.page !== undefined) query.set("page", String(params.page));
  if (params?.size !== undefined) query.set("size", String(params.size));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return http.get<PageResponse<NotificationResponse>>(`/api/notifications${suffix}`, { auth: true });
}

export function getUnreadNotificationCount(): Promise<NotificationUnreadCountResponse> {
  return http.get<NotificationUnreadCountResponse>("/api/notifications/unread-count", { auth: true });
}

export function markNotificationRead(notificationId: number): Promise<void> {
  return http.patch<void>(`/api/notifications/${notificationId}/read`, undefined, { auth: true });
}

export function markAllNotificationsRead(): Promise<void> {
  return http.patch<void>("/api/notifications/read-all", undefined, { auth: true });
}
