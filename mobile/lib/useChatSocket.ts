/**
 * useChatSocket.ts — STOMP 실시간 채팅 소켓 훅
 *
 * 근거: `20260704-채팅시스템고도화-design-fe-app.md` "방안 비교 — 실시간 상태"·"재연결/백필",
 *       BE TDD(`20260704-채팅시스템고도화-tdd.md`) "STOMP 계약".
 *       티켓 `FE-06-chat-socket-hook.md`.
 *
 * 컴포넌트는 STOMP `Client`를 직접 다루지 않고 이 훅을 통해서만 소켓에 접근한다
 * (`no-logic-in-component`). 서버 상태(수신 메시지)는 Zustand 등 별도 스토어에
 * 복사하지 않고 TanStack Query 캐시(`messagesQueryKey`)에만 병합한다
 * (`no-global-by-default`).
 *
 * 단계 경계 (FR-6/7/8 vs FR-10):
 * - 1단계(FR-6/7/8, 이 파일이 전담): 실시간 송수신·타이핑·읽음, 지수 백오프 재연결,
 *   3회 연속 실패 시 REST 폴링 폴백 신호(`pollingFallback`).
 * - 2단계(FR-10): 재연결 후 backfill 보정. FE-05(`GET /rooms/{id}/messages/backfill`)가
 *   별도 브랜치에서 구현 중이므로, 이 파일은 FE-05의 구현 파일을 import하지 않고
 *   `backfill` 콜백을 옵션으로 주입받아 호출한다(Single Writer 원칙).
 *   `backfill` 콜백이 주입되지 않으면(2단계 미배포) 재연결 시 backfill을 건너뛴다 —
 *   `chat.realtime.enabled` 플래그 하위에서 1단계와 독립적으로 켜고 끌 수 있는 조건 분기.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Client, IMessage, ReconnectionTimeMode } from '@stomp/stompjs';

import {
  normalizeBroadcastMessage,
  type BroadcastMessage,
  type ChatMessage,
  type ReadEvent,
  type TypingEvent,
} from '../api/chat-types';
import { sendMessage as sendMessageRest } from '../api/room';
import type { ListMessagesResponse } from '../api/types';
import { useAuthStore } from './auth';
import { useNetInfo } from './netinfo';
import { messagesQueryKey } from './useRooms';

/** 재연결 초기 지연(ms) — `reconnectTimeMode: EXPONENTIAL`로 시도마다 2배씩 증가한다. */
const RECONNECT_BASE_DELAY_MS = 1000;
/** 재연결 최대 지연(ms) 상한. */
const RECONNECT_MAX_DELAY_MS = 30000;
/** 이 횟수만큼 연속으로 연결에 실패하면 REST 폴링 폴백 신호를 노출한다. */
const POLLING_FALLBACK_THRESHOLD = 3;

/**
 * `chat.realtime.enabled` 기능 플래그.
 * OFF(`'false'`)면 소켓을 연결하지 않고 REST 폴백만 사용한다.
 * 롤백: `EXPO_PUBLIC_CHAT_REALTIME_ENABLED=false`로 즉시 비활성화.
 */
export function isChatRealtimeEnabled(): boolean {
  return process.env.EXPO_PUBLIC_CHAT_REALTIME_ENABLED !== 'false';
}

/** `EXPO_PUBLIC_API_URL`(http/https)을 STOMP 브로커 WebSocket URL(ws/wss + /ws)로 변환한다. */
function buildBrokerUrl(apiUrl: string): string {
  const withoutTrailingSlash = apiUrl.replace(/\/+$/, '');
  const webSocketUrl = withoutTrailingSlash.startsWith('https')
    ? withoutTrailingSlash.replace(/^https/, 'wss')
    : withoutTrailingSlash.replace(/^http/, 'ws');
  return `${webSocketUrl}/ws`;
}

/** `value`가 null이 아닌 object인지 좁힌다 — 아래 페이로드 가드들의 공통 전제 조건. */
function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/**
 * STOMP 인바운드 프레임은 신뢰할 수 없는 외부 데이터다(`no-loose-assertion`).
 * 필드 타입까지 확인해 `BroadcastMessage`로 좁힌다 — 필드 누락 시 캐시에 `id: undefined`
 * 같은 오염된 메시지가 유입되는 것을 막는다.
 */
function isBroadcastMessage(value: unknown): value is BroadcastMessage {
  return (
    isRecord(value) &&
    typeof value.messageId === 'number' &&
    typeof value.userId === 'number' &&
    typeof value.content === 'string' &&
    typeof value.createdAt === 'string'
  );
}

/** STOMP 인바운드 프레임을 `TypingEvent`로 좁힌다. */
function isTypingEvent(value: unknown): value is TypingEvent {
  return isRecord(value) && typeof value.userId === 'number' && typeof value.typing === 'boolean';
}

/** STOMP 인바운드 프레임을 `ReadEvent`로 좁힌다. */
function isReadEvent(value: unknown): value is ReadEvent {
  return (
    isRecord(value) &&
    typeof value.userId === 'number' &&
    typeof value.lastReadMessageId === 'number'
  );
}

/**
 * STOMP 인바운드 프레임 body(JSON 문자열)를 안전하게 파싱한다.
 * 깨진 JSON이거나 타입 가드를 통과하지 못하면 `null`을 반환해 프레임을 드롭한다
 * (검증 없는 `as` 단언 대신 타입 가드로 좁히기 — `no-loose-assertion`).
 */
function parseStompFrame<T>(
  rawBody: string,
  isValidPayload: (value: unknown) => value is T
): T | null {
  let parsedBody: unknown;
  try {
    parsedBody = JSON.parse(rawBody);
  } catch {
    return null;
  }
  return isValidPayload(parsedBody) ? parsedBody : null;
}

/** 수신 메시지를 messagesQueryKey 캐시에 append한다. 동일 id는 멱등하게 무시한다(dedup). */
function appendChatMessage(
  previous: ListMessagesResponse | undefined,
  incoming: ChatMessage
): ListMessagesResponse {
  const existingMessages = previous?.messages ?? [];
  const alreadyExists = existingMessages.some((message) => message.id === incoming.id);
  if (alreadyExists) {
    return previous ?? { messages: existingMessages, nextCursor: null };
  }
  return {
    messages: [...existingMessages, incoming],
    nextCursor: previous?.nextCursor ?? null,
  };
}

export interface UseChatSocketOptions {
  roomId: number;
  /**
   * 재연결 성공(재구독) 시 마지막 수신 id 이후 구간을 채우는 콜백 — FR-10(2단계).
   * FE-05의 backfill 함수와 동일 시그니처를 화면에서 주입한다.
   * 주입되지 않으면 backfill을 건너뛴다.
   */
  backfill?: (roomId: number, afterMessageId: number) => Promise<ChatMessage[]>;
  /** 타이핑 이벤트 수신 콜백(휘발성, 실패 무음). */
  onTyping?: (event: TypingEvent) => void;
  /** 읽음 이벤트 수신 콜백(휘발성, 실패 무음). */
  onRead?: (event: ReadEvent) => void;
}

export interface UseChatSocketResult {
  /** 현재 STOMP 연결 여부. */
  isConnected: boolean;
  /** 3회 연속 재연결 실패 시 true — 방 화면이 REST refetchInterval을 활성화하는 신호. */
  pollingFallback: boolean;
  /** SEND /app/rooms/{roomId}/send. 미연결 시 REST POST로 폴백한다. */
  send: (content: string) => Promise<void>;
  /** SEND /app/rooms/{roomId}/typing. 미연결 시 무음 무시(휘발성). */
  sendTyping: (typing: boolean) => void;
  /** SEND /app/rooms/{roomId}/read. 미연결 시 무음 무시(휘발성). */
  sendRead: (lastReadMessageId: number) => void;
}

export function useChatSocket(options: UseChatSocketOptions): UseChatSocketResult {
  const { roomId, backfill, onTyping, onRead } = options;
  const queryClient = useQueryClient();
  const accessToken = useAuthStore((state) => state.accessToken);
  const netInfo = useNetInfo();

  const [isConnected, setIsConnected] = useState(false);
  const [pollingFallback, setPollingFallback] = useState(false);

  const clientRef = useRef<Client | null>(null);
  const consecutiveFailureCountRef = useRef(0);
  const hasConnectedOnceRef = useRef(false);
  const lastReceivedMessageIdRef = useRef<number | null>(null);

  /**
   * `backfill`/`onTyping`/`onRead`의 최신 값을 ref로 보관한다.
   * 호출 화면이 인라인 핸들러(매 렌더 새 함수 identity)를 넘겨도, 이 ref는 렌더마다
   * 갱신될 뿐 아래 소켓 연결 effect의 의존성에는 포함되지 않아 재연결을 유발하지 않는다.
   */
  const backfillRef = useRef(backfill);
  backfillRef.current = backfill;
  const onTypingRef = useRef(onTyping);
  onTypingRef.current = onTyping;
  const onReadRef = useRef(onRead);
  onReadRef.current = onRead;

  useEffect(() => {
    if (!isChatRealtimeEnabled()) {
      return undefined;
    }
    const apiUrl = process.env.EXPO_PUBLIC_API_URL;
    if (!apiUrl || !accessToken) {
      return undefined;
    }
    if (netInfo.isConnected === false) {
      return undefined;
    }

    const brokerUrl = buildBrokerUrl(apiUrl);

    const client = new Client({
      brokerURL: brokerUrl,
      // RN 전역 WebSocket을 사용 — stompjs의 브라우저 WebSocket 타입과
      // React Native 전역 WebSocket은 런타임 형태가 동일하므로 인터페이스 캐스팅한다.
      webSocketFactory: () => new WebSocket(brokerUrl) as unknown as WebSocket,
      connectHeaders: {
        Authorization: `Bearer ${accessToken}`,
      },
      reconnectDelay: RECONNECT_BASE_DELAY_MS,
      maxReconnectDelay: RECONNECT_MAX_DELAY_MS,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      onConnect: () => {
        consecutiveFailureCountRef.current = 0;
        setPollingFallback(false);
        setIsConnected(true);

        client.subscribe(`/topic/rooms/${roomId}`, (message: IMessage) => {
          const broadcast = parseStompFrame(message.body, isBroadcastMessage);
          if (!broadcast) {
            return;
          }
          const normalized = normalizeBroadcastMessage(broadcast, roomId);
          lastReceivedMessageIdRef.current = normalized.id;
          queryClient.setQueryData<ListMessagesResponse>(messagesQueryKey(roomId), (previous) =>
            appendChatMessage(previous, normalized)
          );
        });

        client.subscribe(`/topic/rooms/${roomId}/typing`, (message: IMessage) => {
          const event = parseStompFrame(message.body, isTypingEvent);
          if (!event) {
            return;
          }
          onTypingRef.current?.(event);
        });

        client.subscribe(`/topic/rooms/${roomId}/read`, (message: IMessage) => {
          const event = parseStompFrame(message.body, isReadEvent);
          if (!event) {
            return;
          }
          onReadRef.current?.(event);
        });

        // 2단계(FR-10) 경계: 최초 연결이 아니고(재연결), backfill 콜백이 주입된 경우에만 실행한다.
        const backfillFn = backfillRef.current;
        if (
          hasConnectedOnceRef.current &&
          backfillFn &&
          lastReceivedMessageIdRef.current !== null
        ) {
          const afterMessageId = lastReceivedMessageIdRef.current;
          void backfillFn(roomId, afterMessageId)
            .then((backfilledMessages) => {
              backfilledMessages.forEach((backfilledMessage) => {
                queryClient.setQueryData<ListMessagesResponse>(
                  messagesQueryKey(roomId),
                  (previous) => appendChatMessage(previous, backfilledMessage)
                );
              });
            })
            .catch(() => {
              // backfill 실패는 무음 처리한다 — 다음 재연결 시 재시도되며, dedup(멱등)으로 안전하다.
            });
        }
        hasConnectedOnceRef.current = true;
      },
      onWebSocketClose: () => {
        setIsConnected(false);
        consecutiveFailureCountRef.current += 1;
        if (consecutiveFailureCountRef.current >= POLLING_FALLBACK_THRESHOLD) {
          setPollingFallback(true);
        }
      },
      onStompError: () => {
        setIsConnected(false);
      },
    });

    clientRef.current = client;
    client.activate();

    return () => {
      clientRef.current = null;
      void client.deactivate();
    };
    // `backfill`/`onTyping`/`onRead`는 ref(backfillRef/onTypingRef/onReadRef)로 최신값을
    // 참조하므로 의존성에서 제외한다 — 포함 시 호출 화면의 인라인 콜백이 매 렌더 새로
    // 생성될 때마다 소켓이 재연결돼 실시간성이 훼손된다.
  }, [roomId, accessToken, queryClient, netInfo.isConnected]);

  const send = useCallback(
    async (content: string) => {
      const client = clientRef.current;
      if (client?.connected) {
        client.publish({
          destination: `/app/rooms/${roomId}/send`,
          body: JSON.stringify({ content }),
        });
        return;
      }
      await sendMessageRest(roomId, { content });
    },
    [roomId]
  );

  const sendTyping = useCallback(
    (typing: boolean) => {
      const client = clientRef.current;
      if (!client?.connected) {
        return;
      }
      client.publish({
        destination: `/app/rooms/${roomId}/typing`,
        body: JSON.stringify({ typing }),
      });
    },
    [roomId]
  );

  const sendRead = useCallback(
    (lastReadMessageId: number) => {
      const client = clientRef.current;
      if (!client?.connected) {
        return;
      }
      client.publish({
        destination: `/app/rooms/${roomId}/read`,
        body: JSON.stringify({ lastReadMessageId }),
      });
    },
    [roomId]
  );

  return { isConnected, pollingFallback, send, sendTyping, sendRead };
}
