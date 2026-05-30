/**
 * 마이페이지 탭 — 로그인한 사용자 정보 + 로그아웃.
 * accessToken(JWT)에서 email/roles를 디코딩해 표시한다.
 */
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';
import { useAuthStore } from '../../lib/auth';

interface JwtPayload {
  sub?: string;
  email?: string;
  roles?: string[];
}

function decodeJwt(token: string | null): JwtPayload | null {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const json =
      typeof atob === 'function' ? atob(padded) : Buffer.from(padded, 'base64').toString('utf-8');
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}

export default function MeScreen() {
  const router = useRouter();
  const accessToken = useAuthStore((s) => s.accessToken);
  const logout = useAuthStore((s) => s.logout);

  const payload = decodeJwt(accessToken);

  async function handleLogout() {
    await logout();
    router.replace('/(auth)/login');
  }

  return (
    <View style={styles.container} accessibilityLabel="마이페이지 화면">
      <Text style={styles.title}>마이페이지</Text>

      <View style={styles.card}>
        <Text style={styles.label}>이메일</Text>
        <Text style={styles.value}>{payload?.email ?? '로그인 정보 없음'}</Text>

        <Text style={[styles.label, styles.mt]}>사용자 ID</Text>
        <Text style={styles.value}>{payload?.sub ?? '-'}</Text>

        <Text style={[styles.label, styles.mt]}>역할</Text>
        <Text style={styles.value}>
          {payload?.roles && payload.roles.length > 0 ? payload.roles.join(', ') : 'USER'}
        </Text>
      </View>

      <Pressable
        style={styles.logoutButton}
        onPress={() => void handleLogout()}
        accessibilityRole="button"
        accessibilityLabel="로그아웃"
      >
        <Text style={styles.logoutText}>로그아웃</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff', padding: 24, paddingTop: 64 },
  title: { fontSize: 28, fontWeight: 'bold', color: '#1C1C1E', marginBottom: 24 },
  card: {
    borderWidth: 1,
    borderColor: '#E5E5EA',
    borderRadius: 12,
    padding: 16,
    marginBottom: 24,
  },
  label: { fontSize: 12, color: '#8E8E93' },
  value: { fontSize: 16, color: '#1C1C1E', marginTop: 2 },
  mt: { marginTop: 14 },
  logoutButton: {
    borderWidth: 1,
    borderColor: '#FF3B30',
    borderRadius: 8,
    paddingVertical: 14,
    alignItems: 'center',
  },
  logoutText: { color: '#FF3B30', fontSize: 16, fontWeight: '600' },
});
