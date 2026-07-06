/**
 * MessageBubble — 채팅 메시지 1건을 내/상대 정렬로 렌더하는 프레젠테이션 컴포넌트.
 *
 * 근거: `20260704-채팅시스템고도화-design-fe-app.md` "컴포넌트 트리"
 * (`MessageBubble(props: message, isMine, isRead)`), 티켓 `FE-10-room-chat-realtime-screen.md`.
 *
 * 순수 프레젠테이션 — 읽음 계산·정렬 판단은 컨테이너(`app/rooms/[id].tsx`)가 수행하고
 * 이 컴포넌트는 props만 그대로 렌더한다.
 */
import { StyleSheet, Text, View } from 'react-native';

import type { MessageResponse } from '../../api/types';
import { useTheme } from '../../theme/useTheme';

export interface MessageBubbleProps {
  message: MessageResponse;
  /** 현재 로그인 사용자가 보낸 메시지인지 여부. */
  isMine: boolean;
  /** 내 메시지가 상대에게 읽혔는지 여부(상대 메시지에는 사용하지 않음). */
  isRead: boolean;
}

function formatSentTime(sentAt: string): string {
  return new Date(sentAt).toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function MessageBubble({ message, isMine, isRead }: MessageBubbleProps) {
  const { tokens } = useTheme();
  const bubbleBackground = isMine ? tokens.bubbleMine : tokens.bubbleOther;
  const bubbleTextColor = isMine ? tokens.bubbleMineText : tokens.bubbleOtherText;

  return (
    <View
      testID="message-bubble-row"
      style={[styles.row, isMine ? styles.rowMine : styles.rowOther]}
    >
      <View
        testID="message-bubble"
        style={[styles.bubble, { backgroundColor: bubbleBackground }]}
        accessible
        accessibilityRole="text"
        accessibilityLabel={`메시지: ${message.content}`}
      >
        <Text style={[styles.content, { color: bubbleTextColor }]}>{message.content}</Text>
        <View style={styles.metaRow}>
          {isMine && isRead ? (
            <Text style={[styles.meta, { color: tokens.success }]}>읽음</Text>
          ) : null}
          <Text style={[styles.meta, { color: tokens.textTertiary }]}>
            {formatSentTime(message.sentAt)}
          </Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    width: '100%',
    marginBottom: 8,
  },
  rowMine: {
    alignItems: 'flex-end',
  },
  rowOther: {
    alignItems: 'flex-start',
  },
  bubble: {
    maxWidth: '85%',
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  content: {
    fontSize: 15,
    lineHeight: 20,
  },
  metaRow: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    marginTop: 4,
    gap: 6,
  },
  meta: {
    fontSize: 11,
  },
});
