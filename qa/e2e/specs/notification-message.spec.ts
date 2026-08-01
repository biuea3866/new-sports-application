/**
 * E2E-08 알림 · 메시지
 * 시나리오: qa/e2e/scenarios/notification-message.md
 *
 * AUTH-04: /notifications/**, /rooms/** 는 X-User-Id 헤더 → JWT(@AuthenticationPrincipal
 * UserPrincipal) 인증으로 전환됐다. 모든 케이스는 test/helpers.ts 의 registerAndLogin() 으로
 * 케이스마다 신규 사용자를 만들고 Authorization: Bearer <accessToken> 으로 호출한다.
 *
 * 알림(notification)은 사용자가 직접 생성할 수 있는 API 가 없다(발송은 /admin/notifications/send,
 * ADMIN 권한 전용) — 신규 user 는 항상 알림 0건이므로 목록·미읽음수 케이스는 시드 유무와 무관하게
 * 결정적으로 동작한다. 방(room)·메시지는 POST /rooms 로 스스로 방을 만들 수 있어(참여자는 요청자
 * 본인이 자동 포함) 시드 없이도 소유권·권한 케이스를 결정적으로 재현한다.
 */
import { test, expect, request as playwrightRequest } from "@playwright/test";
import { API_URL, uniqueKey, registerAndLogin, bearer } from "../test/helpers";

test.describe("E2E-08 notification · message", () => {
  test("E2E-08-01 신규 user — GET /notifications/me 는 200 + 빈 Page 를 반환한다", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e08-01");
    const res = await api.get(`${API_URL}/notifications/me`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body.content)).toBe(true);
    expect(body.content.length).toBe(0);
    expect(body.totalElements).toBe(0);
    await api.dispose();
  });

  test("E2E-08-02 GET /notifications/me?onlyUnread=true — 결과는 모두 미읽음(신규 user 는 빈 배열)", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e08-02");
    const res = await api.get(`${API_URL}/notifications/me?onlyUnread=true`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    for (const n of body.content ?? []) {
      expect(n.status === "SENT" || n.readAt === null).toBeTruthy();
    }
    await api.dispose();
  });

  test("E2E-08-03 신규 user — GET /notifications/me/unread-count 는 200 + unreadCount 0", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e08-03");
    const res = await api.get(`${API_URL}/notifications/me/unread-count`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.unreadCount).toBe(0);
    await api.dispose();
  });

  test("E2E-08-04 존재하지 않는 notification id PATCH .../read 시 404", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e08-04");
    const res = await api.patch(`${API_URL}/notifications/999999999/read`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(404);
    await api.dispose();
  });

  test("E2E-08-05 신규 user — POST /rooms 로 방을 만들고 본인이 메시지 전송 시 201", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e08-05");
    const room = await api.post(`${API_URL}/rooms`, {
      headers: { ...bearer(user.accessToken), "Content-Type": "application/json" },
      data: { participantIds: [], name: uniqueKey("qa-room") },
      failOnStatusCode: false,
    });
    expect(room.status()).toBe(201);
    const { id: roomId } = await room.json();
    const res = await api.post(`${API_URL}/rooms/${roomId}/messages`, {
      headers: { ...bearer(user.accessToken), "Content-Type": "application/json" },
      data: { content: "qa-e2e-test-message" },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body).toHaveProperty("id");
    await api.dispose();
  });

  test("E2E-08-06 본인이 만든 방의 메시지 목록 조회 시 200 + 방금 보낸 메시지 포함", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e08-06");
    const room = await api.post(`${API_URL}/rooms`, {
      headers: { ...bearer(user.accessToken), "Content-Type": "application/json" },
      data: { participantIds: [], name: uniqueKey("qa-room") },
      failOnStatusCode: false,
    });
    expect(room.status()).toBe(201);
    const { id: roomId } = await room.json();
    await api.post(`${API_URL}/rooms/${roomId}/messages`, {
      headers: { ...bearer(user.accessToken), "Content-Type": "application/json" },
      data: { content: "qa-e2e-list-message" },
      failOnStatusCode: false,
    });
    const res = await api.get(`${API_URL}/rooms/${roomId}/messages`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body.messages)).toBe(true);
    expect(body.messages.some((m: { content?: string }) => m.content === "qa-e2e-list-message")).toBe(true);
    await api.dispose();
  });

  test("E2E-08-R01 메시지 목록 응답의 cursor 필드는 값이 있을 때만 문자열이다", async () => {
    // ListMessagesResponse.nextCursor 는 null 이면 Jackson 기본 설정(NON_NULL)에 의해 JSON에서
    // 아예 생략된다 — 30건 이하(PAGE_SIZE 미만)인 신규 방은 nextCursor 가 없는 것이 정상이다.
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e08-r01");
    const room = await api.post(`${API_URL}/rooms`, {
      headers: { ...bearer(user.accessToken), "Content-Type": "application/json" },
      data: { participantIds: [], name: uniqueKey("qa-room") },
      failOnStatusCode: false,
    });
    expect(room.status()).toBe(201);
    const { id: roomId } = await room.json();
    const res = await api.get(`${API_URL}/rooms/${roomId}/messages`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body.messages)).toBe(true);
    expect(body.nextCursor === undefined || typeof body.nextCursor === "string").toBe(true);
    await api.dispose();
  });

  test("E2E-08-R02 알림 페이징 기본값 — size 미명시 시 기본 20", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e08-r02");
    const res = await api.get(`${API_URL}/notifications/me`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    if (body.size !== undefined) {
      expect(body.size).toBe(20);
    }
    await api.dispose();
  });

  test("E2E-08-E01 Authorization 헤더 없이 GET /notifications/me 호출 시 401 (AUTH-04 계약)", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.get(`${API_URL}/notifications/me`, { failOnStatusCode: false });
    expect(res.status()).toBe(401);
    await api.dispose();
  });

  test("E2E-08-E02 방 참여자가 아닌 user 가 POST /rooms/{id}/messages 시도 시 403", async () => {
    const apiOwner = await playwrightRequest.newContext();
    const apiIntruder = await playwrightRequest.newContext();
    const owner = await registerAndLogin(apiOwner, "e2e08-e02-owner");
    const intruder = await registerAndLogin(apiIntruder, "e2e08-e02-intruder");
    const room = await apiOwner.post(`${API_URL}/rooms`, {
      headers: { ...bearer(owner.accessToken), "Content-Type": "application/json" },
      data: { participantIds: [], name: uniqueKey("qa-room") },
      failOnStatusCode: false,
    });
    expect(room.status()).toBe(201);
    const { id: roomId } = await room.json();
    const res = await apiIntruder.post(`${API_URL}/rooms/${roomId}/messages`, {
      headers: { ...bearer(intruder.accessToken), "Content-Type": "application/json" },
      data: { content: "intruder" },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
    await apiOwner.dispose();
    await apiIntruder.dispose();
  });

  test("E2E-08-E03 방 참여자가 아닌 user 가 GET /rooms/{id}/messages 조회 시 403", async () => {
    const apiOwner = await playwrightRequest.newContext();
    const apiIntruder = await playwrightRequest.newContext();
    const owner = await registerAndLogin(apiOwner, "e2e08-e03-owner");
    const intruder = await registerAndLogin(apiIntruder, "e2e08-e03-intruder");
    const room = await apiOwner.post(`${API_URL}/rooms`, {
      headers: { ...bearer(owner.accessToken), "Content-Type": "application/json" },
      data: { participantIds: [], name: uniqueKey("qa-room") },
      failOnStatusCode: false,
    });
    expect(room.status()).toBe(201);
    const { id: roomId } = await room.json();
    const res = await apiIntruder.get(`${API_URL}/rooms/${roomId}/messages`, {
      headers: bearer(intruder.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
    await apiOwner.dispose();
    await apiIntruder.dispose();
  });

  test("E2E-08-E04 빈 메시지 내용 POST 시 400", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e08-e04");
    const room = await api.post(`${API_URL}/rooms`, {
      headers: { ...bearer(user.accessToken), "Content-Type": "application/json" },
      data: { participantIds: [], name: uniqueKey("qa-room") },
      failOnStatusCode: false,
    });
    expect(room.status()).toBe(201);
    const { id: roomId } = await room.json();
    const res = await api.post(`${API_URL}/rooms/${roomId}/messages`, {
      headers: { ...bearer(user.accessToken), "Content-Type": "application/json" },
      data: { content: "" },
      failOnStatusCode: false,
    });
    expect([400, 422]).toContain(res.status());
    await api.dispose();
  });
});
