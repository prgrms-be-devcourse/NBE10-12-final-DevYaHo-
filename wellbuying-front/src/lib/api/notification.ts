import { API_BASE_URL, http, reissueTokens } from "@/lib/api/http";
import type { NotificationResponse, NotificationUnreadCountResponse, PageResponse } from "@/lib/api/types";
import { clearTokens, getAccessToken, getDeviceId } from "@/lib/auth/token-storage";

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

const RECONNECT_DELAY_MS = 3_000;

// 네이티브 EventSource는 커스텀 헤더(Authorization)를 실을 수 없어, fetch + ReadableStream으로
// SSE 프로토콜을 직접 파싱한다. 백엔드는 GET /api/notifications/stream에서 인메모리로 붙어있는
// 연결에 알림 발생 시점마다 "notification" 이벤트를 흘려보낸다(단일 서버 전제, 서버 증설 시
// Redis Pub/Sub 등으로 교체 필요 - NotificationSseService 참고).
export function subscribeNotificationStream(onNotification: (notification: NotificationResponse) => void): () => void {
  let stopped = false;
  let abortController: AbortController | null = null;
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  function scheduleReconnect(delayMs: number) {
    if (stopped || reconnectTimer) return;
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null;
      connect();
    }, delayMs);
  }

  async function readStream(body: ReadableStream<Uint8Array>) {
    const reader = body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    for (;;) {
      const { value, done } = await reader.read();
      if (done) return;
      buffer += decoder.decode(value, { stream: true });

      let separatorIndex = buffer.indexOf("\n\n");
      while (separatorIndex !== -1) {
        dispatchFrame(buffer.slice(0, separatorIndex));
        buffer = buffer.slice(separatorIndex + 2);
        separatorIndex = buffer.indexOf("\n\n");
      }
    }
  }

  function dispatchFrame(rawFrame: string) {
    let eventName = "message";
    const dataLines: string[] = [];
    for (const line of rawFrame.split("\n")) {
      if (line.startsWith("event:")) eventName = line.slice("event:".length).trim();
      else if (line.startsWith("data:")) dataLines.push(line.slice("data:".length).trim());
    }
    // "connected" 핸드셰이크 이벤트와 하트비트 코멘트(:ping)는 무시하고 실제 알림만 처리한다
    if (eventName !== "notification" || dataLines.length === 0) return;
    try {
      onNotification(JSON.parse(dataLines.join("\n")));
    } catch {
      // 파싱 실패한 프레임은 다음 프레임에 영향 없이 건너뛴다
    }
  }

  async function connect() {
    if (stopped) return;
    const accessToken = getAccessToken();
    if (!accessToken) return;

    abortController = new AbortController();
    try {
      const deviceId = getDeviceId();
      const response = await fetch(`${API_BASE_URL}/api/notifications/stream`, {
        credentials: "include",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          ...(deviceId ? { "X-Device-Id": deviceId } : {}),
        },
        signal: abortController.signal,
      });

      if (response.status === 401) {
        const refreshed = await reissueTokens();
        if (refreshed) {
          scheduleReconnect(0);
        } else {
          clearTokens();
        }
        return;
      }

      if (!response.ok || !response.body) {
        scheduleReconnect(RECONNECT_DELAY_MS);
        return;
      }

      await readStream(response.body);
      scheduleReconnect(RECONNECT_DELAY_MS);
    } catch {
      // AbortError(구독 해제) 포함 - stopped가 아니면 연결이 끊긴 것이므로 재연결 시도
      scheduleReconnect(RECONNECT_DELAY_MS);
    }
  }

  connect();

  return () => {
    stopped = true;
    if (reconnectTimer) clearTimeout(reconnectTimer);
    abortController?.abort();
  };
}
