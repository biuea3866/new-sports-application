/**
 * 탭 네비게이터 레이아웃
 * 탭: 홈 / 스토어 / 티켓 / 커뮤니티 / 마이
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
