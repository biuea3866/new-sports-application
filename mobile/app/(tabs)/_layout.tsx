/**
 * 탭 네비게이터 레이아웃
 * 탭: 홈 / 시설 검색 / 마이페이지
 */
import { Tabs } from 'expo-router';

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: '#007AFF',
        tabBarInactiveTintColor: '#8E8E93',
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
        name="search"
        options={{
          title: '시설 검색',
          tabBarAccessibilityLabel: '시설 검색 탭',
        }}
      />
      <Tabs.Screen
        name="me"
        options={{
          title: '마이페이지',
          tabBarAccessibilityLabel: '마이페이지 탭',
        }}
      />
    </Tabs>
  );
}
