/**
 * TypingIndicator — 상대 타이핑 인디케이터. TTL(3초) 동안만 표시된다.
 *
 * 근거: `20260704-채팅시스템고도화-design-fe-app.md` "컴포넌트 트리"
 * (`TypingIndicator(props: typingUserIds)`), 티켓 `FE-10-room-chat-realtime-screen.md`
 * "TypingIndicator(상대 입력 중, 3초 TTL useState)".
 *
 * 컨테이너는 `useChatSocket`의 `onTyping` 콜백에서 `typing=true` 이벤트를 받을 때마다
 * 최신 시각(`lastTypingAt`)만 넘겨준다. 표시 후 몇 초 뒤 자동으로 사라지는 것은 이 컴포넌트
 * 자신의 렌더링 타이머 책임(자기 자신을 스스로 닫는 토스트류 UI와 동일한 패턴)이라 여기서 처리한다.
 */
import { useEffect, useState } from 'react';
import { StyleSheet, Text } from 'react-native';

import { useTheme } from '../../theme/useTheme';

export interface TypingIndicatorProps {
  /** 상대가 마지막으로 typing=true 이벤트를 보낸 시각(ms epoch). null이면 타이핑 중이 아니다. */
  lastTypingAt: number | null;
}

const TYPING_INDICATOR_TTL_MS = 3000;

export function TypingIndicator({ lastTypingAt }: TypingIndicatorProps) {
  const { tokens } = useTheme();
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    if (lastTypingAt === null) {
      setIsVisible(false);
      return undefined;
    }
    setIsVisible(true);
    const timeoutId = setTimeout(() => setIsVisible(false), TYPING_INDICATOR_TTL_MS);
    return () => clearTimeout(timeoutId);
  }, [lastTypingAt]);

  if (!isVisible) {
    return null;
  }

  return (
    <Text style={[styles.text, { color: tokens.typing }]} accessibilityRole="text">
      상대가 입력 중…
    </Text>
  );
}

const styles = StyleSheet.create({
  text: {
    fontSize: 13,
    paddingHorizontal: 16,
    paddingVertical: 4,
  },
});
