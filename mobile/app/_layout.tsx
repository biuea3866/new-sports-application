/**
 * 루트 레이아웃 — expo-router 앱 진입점
 *
 * SecureStore에 refreshToken이 있으면 홈 탭, 없으면 로그인으로 리다이렉트합니다.
 */
import { Stack } from 'expo-router';
import { useEffect } from 'react';
import * as SecureStore from 'expo-secure-store';
import { useRouter, useSegments } from 'expo-router';
import { useAuthStore } from '../lib/auth';

function AuthGuard() {
  const { accessToken, setAccessToken } = useAuthStore();
  const segments = useSegments();
  const router = useRouter();

  useEffect(() => {
    void (async () => {
      const refreshToken = await SecureStore.getItemAsync('refreshToken');
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
    <>
      <AuthGuard />
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="(auth)" options={{ headerShown: false }} />
      </Stack>
    </>
  );
}
