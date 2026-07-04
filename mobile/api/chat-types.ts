/**
 * chat-types.ts — 채팅 도메인 신규 DTO 타입 (FE-02)
 *
 * 근거: `20260704-채팅시스템고도화-tdd.md` "응답 DTO 필드 스키마"·"STOMP 계약",
 *       `20260704-채팅시스템고도화-design-fe-app.md` "API 연동 표"·"STOMP↔REST 필드 정규화".
 *
 * Single Writer 원칙 — 기존 `api/types.ts`(RoomResponse/MessageResponse 등)는 수정하지 않고
 * 신규 채팅 관련 타입만 이 파일에 정의한다.
 */

// --- STOMP 페이로드 ---

/** SUBSCRIBE /topic/rooms/{roomId} 수신 페이로드 */
export interface BroadcastMessage {
  messageId: number;
  userId: number;
  content: string;
  createdAt: string; // ISO-8601 (offset 포함)
}

/** SEND/SUBSCRIBE /topic/rooms/{roomId}/typing 페이로드 */
export interface TypingEvent {
  userId: number;
  typing: boolean;
}

/** SEND /app/rooms/{roomId}/read → /topic/rooms/{roomId}/read 페이로드 */
export interface ReadEvent {
  userId: number;
  lastReadMessageId: number;
}

// --- FE 내부 정규화 타입 ---

/**
 * REST `MessageResponse`와 STOMP `BroadcastMessage`를 하나로 병합한 FE 내부 표현.
 * REST `MessageResponse`는 이미 이 형태({id,roomId,senderId,content,sentAt})와 동일하다.
 */
export interface ChatMessage {
  id: number;
  roomId: number;
  senderId: number;
  content: string;
  sentAt: string; // ISO-8601
}

/**
 * STOMP↔REST 필드 정규화: messageId→id, userId→senderId, createdAt→sentAt, content→content.
 * roomId는 BroadcastMessage에 없으므로 구독 destination에서 얻은 값을 인자로 받는다.
 */
export function normalizeBroadcastMessage(
  broadcast: BroadcastMessage,
  roomId: number
): ChatMessage {
  return {
    id: broadcast.messageId,
    roomId,
    senderId: broadcast.userId,
    content: broadcast.content,
    sentAt: broadcast.createdAt,
  };
}

// --- 읽음 / 안읽은 ---

/** GET /rooms/me/unread, POST /rooms/{id}/read 응답 */
export interface RoomUnreadResponse {
  roomId: number;
  unreadCount: number; // BE Long → number
}

/** POST /rooms/{roomId}/read 응답 (UnreadResponse — RoomUnreadResponse와 동일 계약) */
export type UnreadResponse = RoomUnreadResponse;

/** POST /rooms/{roomId}/read 요청 */
export interface MarkReadRequest {
  lastReadMessageId: number;
}

// --- 게스트 초대 ---

/** BE 상태 전이 표 5개 값과 일치 (RoomInvitation.accept/reject/revoke/expire) */
export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'REVOKED' | 'EXPIRED';

/** POST /rooms/{roomId}/invitations, GET /rooms/invitations/me, accept/reject 응답 */
export interface InvitationResponse {
  id: number;
  roomId: number;
  inviterUserId: number;
  inviteeUserId: number;
  status: InvitationStatus;
  canSpeak: boolean;
  expiresAt: string; // ISO-8601
  createdAt: string; // ISO-8601
}

/** POST /rooms/{roomId}/invitations 요청 */
export interface InviteGuestRequest {
  inviteeUserId: number;
  canSpeak: boolean;
  expiresInDays: number;
}

// --- Room 확장 (역제안 반영) ---

/** RoomResponse.contextType — 컨텍스트 없으면 null(기존 DIRECT/GROUP) */
export type RoomContextType = 'COMMUNITY' | 'GOODS_PRODUCT';

/**
 * 방 목록 화면(S1) 병합용 FE 내부 타입.
 * `RoomResponse`(+역제안 필드)와 `RoomUnreadResponse`를 roomId로 조합한 형태.
 * BE 확정 대기: RoomResponse의 contextType/contextId 필드는 TDD 표에 contextType만 명시되어
 * contextId는 미정 — 필요 시 optional로 추가한다.
 */
export interface RoomListItem {
  id: number;
  name: string | null;
  contextType: RoomContextType | null;
  lastMessagePreview: string | null;
  lastMessageAt: string | null; // ISO-8601
  unreadCount: number;
}
