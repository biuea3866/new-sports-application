/**
 * 루트 레이아웃 — expo-router 앱 진입점
 *
 * - SecureStore에 refreshToken이 있으면 홈 탭, 없으면 로그인으로 리다이렉트합니다.
 * - QueryClientProvider + persistQueryClient로 TanStack Query를 초기화합니다.
 * - MMKV 영속화: 캐시를 30분간 디스크에 보관합니다.
 */
import { Stack } from 'expo-router';
import { useEffect } from 'react';
import { useRouter, useSegments } from 'expo-router';
import { QueryClientProvider } from '@tanstack/react-query';
import { persistQueryClient } from '@tanstack/query-persist-client-core';
import { useAuthStore } from '../lib/auth';
import { getItem } from '../lib/secure-store';
import { queryClient, mmkvPersister } from '../lib/query-client';
import { ThemeProvider } from '../theme/ThemeProvider';

// 앱 시작 시 한 번만 영속화 구독을 설정합니다.
persistQueryClient({
  queryClient,
  persister: mmkvPersister,
});

function AuthGuard() {
  const { accessToken, setAccessToken } = useAuthStore();
  const segments = useSegments();
  const router = useRouter();

  useEffect(() => {
    void (async () => {
      const refreshToken = await getItem('refreshToken');
      const inAuthGroup = segments[0] === '(auth)';

      if (!refreshToken && !inAuthGroup) {
        // refreshToken 없음 → 로그인으로
        router.replace('/(auth)/login');
      }
    })();
  }, [accessToken, segments, router, setAccessToken]);

  return null;
}

export default function RootLayout() {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthGuard />
        <Stack screenOptions={{ headerShown: false }}>
          <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
          <Stack.Screen name="(auth)" options={{ headerShown: false }} />
          <Stack.Screen name="product/[id]/index" options={{ headerShown: false }} />
          <Stack.Screen name="cart/index" options={{ headerShown: false }} />
          <Stack.Screen name="limited-drop/[id]/index" options={{ headerShown: false }} />
          <Stack.Screen name="limited-drop/[id]/purchase" options={{ headerShown: false }} />
          {/* 채팅 시스템 고도화 — 신규 라우트 등록 (통합 티켓, 각 화면은 자체 헤더를 렌더한다) */}
          <Stack.Screen name="rooms/index" options={{ headerShown: false }} />
          <Stack.Screen name="rooms/[id]" options={{ headerShown: false }} />
          <Stack.Screen name="communities/index" options={{ headerShown: false }} />
          <Stack.Screen name="communities/new" options={{ headerShown: false }} />
          <Stack.Screen name="communities/[id]" options={{ headerShown: false }} />
          <Stack.Screen name="invite/[roomId]" options={{ headerShown: false }} />
          <Stack.Screen name="invitations/index" options={{ headerShown: false }} />
        </Stack>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
