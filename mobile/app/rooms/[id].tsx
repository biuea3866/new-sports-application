/**
 * 채팅방(실시간) 화면 — S2 (FE-10, 재작성)
 *
 * 근거: `20260704-채팅시스템고도화-design-fe-app.md` "S2. 채팅방(실시간)"·"컴포넌트 트리",
 *       `20260704-채팅시스템고도화-tdd.md` "STOMP 계약", 티켓 `FE-10-room-chat-realtime-screen.md`.
 *
 * 컨테이너만 훅(`useMessages`·`useChatSocket`·`useMarkRead`·`useMyProfile`)을 소비하고,
 * 프레젠테이션(`components/chat/*`)은 props + `useTheme()`만 사용한다(`no-logic-in-component`).
 *
 * 확인 필요(미해결 — API 미연동): BE REST 계약에 "현재 사용자의 방 참여자 정보"
 * (`canSpeak`/`expiresAt`/방장 여부) 조회 엔드포인트가 없다. `MessageComposer.canSpeak`는
 * 항상 true, `GuestExpiryBanner.expiresAt`는 항상 null, 헤더 게스트 초대 진입은 항상 숨김으로
 * 배선한다 — BE가 참여자 자기 조회 API를 제공하면 이 파일에서만 연결하면 된다(컴포넌트는 이미
 * props로 완전히 대응 가능).
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { FlatList, KeyboardAvoidingView, Platform, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { AxiosError } from 'axios';

import { useMessages } from '../../lib/useRooms';
import { useChatSocket } from '../../lib/useChatSocket';
import { isFeatureEnabled } from '../../lib/feature-flags';
import { useMarkRead } from '../../lib/useChat';
import { useMyProfile } from '../../lib/useMyProfile';
import type { MessageResponse } from '../../api/types';
import type { ReadEvent, TypingEvent } from '../../api/chat-types';
import {
  Button,
  EmptyState,
  ErrorView,
  LoadingView,
  ThemedText,
  ThemedView,
} from '../../components/ui';
import { MessageBubble } from '../../components/chat/MessageBubble';
import { TypingIndicator } from '../../components/chat/TypingIndicator';
import { MessageComposer } from '../../components/chat/MessageComposer';
import { ConnectionBanner } from '../../components/chat/ConnectionBanner';
import { GuestExpiryBanner } from '../../components/chat/GuestExpiryBanner';

/** REST 폴링 폴백 활성 시 주기적으로 `refetch()`하는 간격(ms). */
const POLLING_FALLBACK_INTERVAL_MS = 5000;

/** `GET /rooms/{roomId}/messages` 실패가 게스트 방출(403)인지 판별한다. */
function isGuestEvictedError(error: unknown): boolean {
  return error instanceof AxiosError && error.response?.status === 403;
}

/** 내 메시지가 상대에게 읽혔는지: 나 이외의 참여자 읽음 커서가 메시지 id 이상인지로 판단한다. */
function isReadByOthers(
  messageId: number,
  myUserId: number | null,
  readCursors: Record<number, number>
): boolean {
  return Object.entries(readCursors).some(([userIdKey, lastReadMessageId]) => {
    const userId = Number(userIdKey);
    if (myUserId !== null && userId === myUserId) {
      return false;
    }
    return lastReadMessageId >= messageId;
  });
}

export default function RoomChatScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const roomId = Number(id);
  const router = useRouter();

  const { data: profile } = useMyProfile();
  const myUserId = profile?.id ?? null;

  const { data, isLoading, isError, error, refetch } = useMessages(roomId);
  const { mutate: markRead } = useMarkRead();

  const [lastTypingAt, setLastTypingAt] = useState<number | null>(null);
  const [readCursors, setReadCursors] = useState<Record<number, number>>({});

  const handleTyping = useCallback(
    (event: TypingEvent) => {
      if (event.typing && event.userId !== myUserId) {
        setLastTypingAt(Date.now());
      }
    },
    [myUserId]
  );

  const handleRead = useCallback((event: ReadEvent) => {
    setReadCursors((previous) => ({ ...previous, [event.userId]: event.lastReadMessageId }));
  }, []);

  const { isConnected, pollingFallback, send, sendTyping } = useChatSocket({
    roomId,
    onTyping: handleTyping,
    onRead: handleRead,
  });

  // 롤백 플래그(`chat.realtime.enabled` OFF) — 소켓을 아예 켜지 않으므로 `isConnected`는 항상
  // false로 고정된다. 이 상태에서 "연결 끊김" 배너를 보여주면 오히려 오해를 준다(의도된 REST 전용
  // 모드이지 장애가 아님) — realtime이 꺼져 있으면 배너를 숨기고, REST 폴링만으로 갱신한다.
  const isRealtimeEnabled = isFeatureEnabled('chat.realtime.enabled');
  const shouldPollAsFallback = !isRealtimeEnabled || pollingFallback;

  // 폴링 폴백 — realtime이 꺼져 있거나(롤백) 3회 연속 재연결 실패 시 주기적으로 REST refetch
  // (design-fe-app.md "방안 비교 — 재연결/백필"·"기능 플래그").
  useEffect(() => {
    if (!shouldPollAsFallback) {
      return undefined;
    }
    const intervalId = setInterval(() => {
      void refetch();
    }, POLLING_FALLBACK_INTERVAL_MS);
    return () => clearInterval(intervalId);
  }, [shouldPollAsFallback, refetch]);

  const messages = useMemo(() => data?.messages ?? [], [data]);
  const latestMessageId = messages.length > 0 ? messages[messages.length - 1].id : null;
  const lastMarkedMessageIdRef = useRef<number | null>(null);

  // 방 진입 시 + 새 메시지 수신 시 read 처리(FR-7/9). 화면이 마운트돼 있는 동안만 갱신한다.
  useEffect(() => {
    if (latestMessageId === null || roomId <= 0) {
      return;
    }
    if (lastMarkedMessageIdRef.current === latestMessageId) {
      return;
    }
    lastMarkedMessageIdRef.current = latestMessageId;
    markRead({ roomId, lastReadMessageId: latestMessageId });
  }, [roomId, latestMessageId, markRead]);

  const handleSend = useCallback(
    (content: string) => {
      void send(content);
    },
    [send]
  );

  const handleTypingChange = useCallback(
    (typing: boolean) => {
      sendTyping(typing);
    },
    [sendTyping]
  );

  const handleGoToList = useCallback(() => {
    router.push('/rooms');
  }, [router]);

  if (isLoading) {
    return (
      <ThemedView style={styles.container}>
        <LoadingView variant="skeleton" />
      </ThemedView>
    );
  }

  if (isError) {
    if (isGuestEvictedError(error)) {
      return (
        <ThemedView style={styles.centered} accessibilityLabel="게스트 참여 만료">
          <ThemedText variant="primary" style={styles.evictedMessage} accessibilityRole="alert">
            참여 기간이 만료되어 대화를 볼 수 없어요
          </ThemedText>
          <Button label="목록으로" onPress={handleGoToList} />
        </ThemedView>
      );
    }
    return (
      <ThemedView style={styles.container}>
        <ErrorView message="메시지를 불러오지 못했어요" onRetry={() => void refetch()} />
      </ThemedView>
    );
  }

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 90 : 0}
    >
      <ThemedView background="background" style={styles.container}>
        {isRealtimeEnabled ? (
          <ConnectionBanner isConnected={isConnected} pollingFallback={pollingFallback} />
        ) : null}
        {/* 확인 필요(API 미연동): 참여자 자기 조회 API 없음 — 항상 숨김(null) */}
        <GuestExpiryBanner expiresAt={null} />

        <View style={styles.messageArea}>
          {messages.length === 0 ? (
            <EmptyState message="첫 메시지를 보내보세요" />
          ) : (
            <FlatList
              data={[...messages].reverse()}
              keyExtractor={(item: MessageResponse) => String(item.id)}
              renderItem={({ item }) => (
                <MessageBubble
                  message={item}
                  isMine={item.senderId === myUserId}
                  isRead={isReadByOthers(item.id, myUserId, readCursors)}
                />
              )}
              inverted
              contentContainerStyle={styles.list}
              accessibilityLabel="메시지 목록"
            />
          )}
        </View>

        <TypingIndicator lastTypingAt={lastTypingAt} />

        <MessageComposer canSpeak onSend={handleSend} onTypingChange={handleTypingChange} />
      </ThemedView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  evictedMessage: {
    fontSize: 16,
    fontWeight: '600',
    textAlign: 'center',
    marginBottom: 20,
  },
  messageArea: {
    flex: 1,
    paddingHorizontal: 16,
  },
  list: {
    paddingTop: 12,
    paddingBottom: 8,
  },
});
