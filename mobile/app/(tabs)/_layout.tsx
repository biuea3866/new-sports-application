/**
 * 탭 네비게이터 레이아웃 — 5탭: 홈 / 시설 / 스토어 / 커뮤니티 / 마이
 *
 * 근거: 사용자 피드백(탭바 아이콘 부재, 7탭 → 5탭 재편).
 * - 스토어 탭은 굿즈|티켓, 커뮤니티 탭은 게시글|동아리를 `SegmentedControl`로
 *   화면 내부에서 통합한다 (`(tabs)/store.tsx`, `(tabs)/community.tsx`).
 * - 채팅은 탭에서 제거하고 홈·커뮤니티 화면 상단의 `ChatEntryButton`으로 진입한다
 *   (기존 전역 안읽은 수 배지는 그 버튼으로 이동했다 — 탭 배지가 아니다).
 * - "search"라는 모호한 탭 이름 대신 실제 기능(내 주변 시설 검색)을 드러내는
 *   "facilities" 파일명·"시설" 라벨을 쓴다.
 *
 * - 활성/비활성 탭 색은 하드코딩 대신 `useTheme()` accent·textTertiary 토큰을 사용한다
 *   (라이트/다크 모두 대응).
 * - 아이콘은 `@expo/vector-icons`의 Ionicons — 웹 번들(react-native-web)에서도
 *   폰트 아이콘으로 렌더된다.
 */
import type { ComponentProps } from 'react';
import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../../theme/useTheme';

type IoniconName = ComponentProps<typeof Ionicons>['name'];

interface TabIconSpec {
  focused: IoniconName;
  unfocused: IoniconName;
}

type TabName = 'index' | 'facilities' | 'store' | 'community' | 'me';

const TAB_ICON_SPECS: Record<TabName, TabIconSpec> = {
  index: { focused: 'home', unfocused: 'home-outline' },
  facilities: { focused: 'location', unfocused: 'location-outline' },
  store: { focused: 'storefront', unfocused: 'storefront-outline' },
  community: { focused: 'people', unfocused: 'people-outline' },
  me: { focused: 'person', unfocused: 'person-outline' },
};

function createTabBarIcon(name: TabName) {
  const spec = TAB_ICON_SPECS[name];

  return function TabBarIcon({
    focused,
    color,
    size,
  }: {
    focused: boolean;
    color: string;
    size: number;
  }) {
    return <Ionicons name={focused ? spec.focused : spec.unfocused} size={size} color={color} />;
  };
}

export default function TabsLayout() {
  const { tokens } = useTheme();

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
          tabBarIcon: createTabBarIcon('index'),
        }}
      />
      <Tabs.Screen
        name="facilities"
        options={{
          title: '시설',
          tabBarAccessibilityLabel: '시설 탭',
          tabBarIcon: createTabBarIcon('facilities'),
        }}
      />
      <Tabs.Screen
        name="store"
        options={{
          title: '스토어',
          tabBarAccessibilityLabel: '스토어 탭',
          tabBarIcon: createTabBarIcon('store'),
        }}
      />
      <Tabs.Screen
        name="community"
        options={{
          title: '커뮤니티',
          tabBarAccessibilityLabel: '커뮤니티 탭',
          tabBarIcon: createTabBarIcon('community'),
        }}
      />
      <Tabs.Screen
        name="me"
        options={{
          title: '마이',
          tabBarAccessibilityLabel: '마이 탭',
          tabBarIcon: createTabBarIcon('me'),
        }}
      />
    </Tabs>
  );
}
