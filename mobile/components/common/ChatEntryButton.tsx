/**
 * ChatEntryButton — 채팅방 목록(/rooms) 진입 아이콘 버튼.
 *
 * 근거: 사용자 피드백 "채팅은 탭에서 제거 → 홈·커뮤니티 화면 상단 우측 아이콘으로 진입,
 * 기존 전역 안읽은 수 배지(useTotalUnread)를 그 아이콘에 표시". 채팅방 목록 자체는
 * `app/rooms/index.tsx`(루트 스택)가 그대로 담당한다 — 이 버튼은 진입점일 뿐이다.
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

import { Badge } from '../ui/Badge';
import { useTheme } from '../../theme/useTheme';
import { useTotalUnread } from '../../lib/useTotalUnread';

export function ChatEntryButton() {
  const router = useRouter();
  const { tokens } = useTheme();
  const totalUnread = useTotalUnread();
  const accessibilityLabel = totalUnread > 0 ? `채팅, 안읽은 메시지 ${totalUnread}개` : '채팅';

  return (
    <Pressable
      style={styles.button}
      onPress={() => router.push('/rooms')}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
    >
      <Ionicons
        name="chatbubble-ellipses-outline"
        size={24}
        color={tokens.textPrimary}
        testID="chat-entry-icon"
      />
      {totalUnread > 0 ? (
        <View style={styles.badgeOverlay}>
          <Badge count={totalUnread} />
        </View>
      ) : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeOverlay: {
    position: 'absolute',
    top: -2,
    right: -2,
  },
});
