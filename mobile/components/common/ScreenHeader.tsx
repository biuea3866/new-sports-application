/**
 * ScreenHeader — 루트 스택 상세 화면 공용 상단 헤더(뒤로가기 + 선택적 제목).
 *
 * 근거: 사용자 피드백 "채팅방·커뮤니티 상세에서 뒤로가기가 없어 갇힌다" — `app/_layout.tsx`가
 * 루트 스택 상세 화면을 전부 `headerShown: false`로 두므로, 탭바도 헤더 뒤로가기도 없는
 * 화면(`rooms/[id]`·`communities/[id]`)은 이 컴포넌트로 자체 뒤로가기를 렌더한다.
 * `onBack`은 호출부가 넘긴다 — `useRouter().back` / 정적 `router.back` 어느 쪽이든
 * 이 컴포넌트는 router를 직접 참조하지 않는다(호출부 mocking 방식과 무관하게 재사용 가능).
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음, 라이트/다크 대응).
 */
import { Pressable, StyleSheet, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

import { ThemedText } from '../ui';
import { useTheme } from '../../theme/useTheme';

export interface ScreenHeaderProps {
  /** 헤더 제목. 없으면 뒤로가기 버튼만 렌더한다. */
  title?: string;
  /** 뒤로가기 버튼 탭 시 호출. 보통 `router.back()`. */
  onBack: () => void;
}

export function ScreenHeader({ title, onBack }: ScreenHeaderProps) {
  const { tokens } = useTheme();

  return (
    <View
      style={[
        styles.container,
        { backgroundColor: tokens.surface, borderBottomColor: tokens.border },
      ]}
    >
      <Pressable
        style={styles.backButton}
        onPress={onBack}
        accessibilityRole="button"
        accessibilityLabel="뒤로 가기"
        hitSlop={8}
      >
        <Ionicons name="chevron-back" size={26} color={tokens.textPrimary} />
      </Pressable>
      {title !== undefined && title.length > 0 ? (
        <ThemedText
          variant="primary"
          style={styles.title}
          numberOfLines={1}
          accessibilityRole="header"
        >
          {title}
        </ThemedText>
      ) : null}
      <View style={styles.spacer} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingTop: 56,
    paddingBottom: 12,
    paddingHorizontal: 8,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  backButton: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    flex: 1,
    fontSize: 17,
    fontWeight: '600',
    marginLeft: 4,
  },
  spacer: {
    width: 40,
  },
});
