/**
 * ConnectionBanner — 소켓 연결 끊김/재연결/폴링 폴백 상태를 안내하는 배너.
 *
 * 근거: `20260704-채팅시스템고도화-design-fe-app.md` S2 와이어프레임
 * "⚠ 연결 끊김 — 재연결 중… (조건) 재연결 배너 / 폴링 폴백 배너",
 * 티켓 `FE-10-room-chat-realtime-screen.md` 테스트 케이스
 * "연결 끊김 시 재연결 배너가 표시되고 폴링 폴백으로 메시지가 계속 갱신된다".
 *
 * 연결돼 있으면 아무것도 렌더하지 않는다. 컨테이너는 `useChatSocket().isConnected`/
 * `pollingFallback`을 그대로 전달하기만 한다(비즈니스 판단 없음).
 */
import { StyleSheet, Text, View } from 'react-native';

import { useTheme } from '../../theme/useTheme';

export interface ConnectionBannerProps {
  isConnected: boolean;
  /** 3회 연속 재연결 실패로 REST 폴링 폴백이 활성화됐는지 여부. */
  pollingFallback: boolean;
}

const RECONNECTING_MESSAGE = '연결이 끊겼어요. 재연결 중…';
const POLLING_FALLBACK_MESSAGE = '실시간 연결이 어려워 새로고침으로 갱신하고 있어요';

export function ConnectionBanner({ isConnected, pollingFallback }: ConnectionBannerProps) {
  const { tokens } = useTheme();

  if (isConnected) {
    return null;
  }

  const message = pollingFallback ? POLLING_FALLBACK_MESSAGE : RECONNECTING_MESSAGE;

  return (
    <View
      style={[styles.container, { backgroundColor: tokens.surface }]}
      accessible
      accessibilityRole="alert"
      accessibilityLabel={message}
    >
      <Text style={[styles.message, { color: tokens.danger }]}>{message}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  message: {
    fontSize: 13,
    fontWeight: '600',
  },
});
