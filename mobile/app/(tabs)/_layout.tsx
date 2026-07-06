/**
 * 탭 네비게이터 레이아웃
 * 탭: 홈 / 스토어 / 티켓 / 채팅(신규) / 동아리(신규, chat.community.enabled 게이팅) /
 *     커뮤니티(게시판, 기존 유지) / 마이
 *
 * 근거: 티켓 "앱 와이어업·기능 플래그·전역 배지 통합", design-fe-app.md
 * "라우팅·내비게이션 흐름"·"기능 플래그·점진 공개".
 *
 * - 활성/비활성 탭 색은 하드코딩 대신 `useTheme()` accent·textTertiary 토큰을 사용한다
 *   (라이트/다크 모두 대응).
 * - "채팅" 탭은 전역 안읽은 수 합계(`useTotalUnread`)를 배지로 표시한다(0이면 숨김).
 *   실제 UI는 `rooms/index`(FE-09)가 그리므로, 탭 프레스는 `listeners.tabPress`로
 *   가로채 화면 전환 없이 `/rooms`로 이동시킨다(`(tabs)/chat.tsx`는 딥링크 폴백).
 * - "동아리" 탭은 `chat.community.enabled` OFF면 `href: null`로 탭 자체를 숨긴다
 *   (Expo Router 공식 탭 숨김 기법). 탭 프레스는 `/communities`로 이동시킨다
 *   (`(tabs)/clubs.tsx`는 딥링크 폴백).
 */
import { Tabs, useRouter } from 'expo-router';
import { isFeatureEnabled } from '../../lib/feature-flags';
import { useTotalUnread } from '../../lib/useTotalUnread';
import { useTheme } from '../../theme/useTheme';

export default function TabsLayout() {
  const router = useRouter();
  const { tokens } = useTheme();
  const totalUnread = useTotalUnread();
  const isCommunityEnabled = isFeatureEnabled('chat.community.enabled');

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: tokens.accent,
        tabBarInactiveTintColor: tokens.textTertiary,
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: '홈',
          tabBarAccessibilityLabel: '홈 탭',
        }}
      />
      <Tabs.Screen
        name="store"
        options={{
          title: '스토어',
          tabBarAccessibilityLabel: '스토어 탭',
        }}
      />
      <Tabs.Screen
        name="tickets"
        options={{
          title: '티켓',
          tabBarAccessibilityLabel: '티켓 탭',
        }}
      />
      <Tabs.Screen
        name="chat"
        options={{
          title: '채팅',
          tabBarAccessibilityLabel: '채팅 탭',
          tabBarBadge: totalUnread > 0 ? totalUnread : undefined,
          tabBarBadgeStyle: { backgroundColor: tokens.badge, color: tokens.badgeText },
        }}
        listeners={{
          tabPress: (event) => {
            event.preventDefault();
            router.push('/rooms');
          },
        }}
      />
      <Tabs.Screen
        name="clubs"
        options={{
          title: '동아리',
          tabBarAccessibilityLabel: '동아리 탭',
          href: isCommunityEnabled ? undefined : null,
        }}
        listeners={{
          tabPress: (event) => {
            event.preventDefault();
            router.push('/communities');
          },
        }}
      />
      <Tabs.Screen
        name="community"
        options={{
          title: '커뮤니티',
          tabBarAccessibilityLabel: '커뮤니티 탭',
        }}
      />
      <Tabs.Screen
        name="me"
        options={{
          title: '마이',
          tabBarAccessibilityLabel: '마이 탭',
        }}
      />
    </Tabs>
  );
}
