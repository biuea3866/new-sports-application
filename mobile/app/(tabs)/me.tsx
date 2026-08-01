/**
 * 마이페이지 탭 — 로그인한 사용자 정보 + 로그아웃 + 내 주문 내역(통합) 진입점(FE-11).
 * accessToken(JWT)에서 email/roles를 디코딩해 표시한다.
 *
 * 내 주문 내역 진입점은 `orders.unified.enabled` 플래그로 게이팅한다(BE 파사드 API 준비 전
 * 숨김, `20260708-상품주문-공유상위컨텍스트-design-fe-app.md` "Release Scenario").
 */
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';
import { useAuthStore } from '../../lib/auth';
import { ListItem } from '../../components/ui';
import { isFeatureEnabled } from '../../lib/feature-flags';
import { useMyProfile } from '../../lib/useMyProfile';
import { ROUTES } from '../../lib/navigation';
import { formatUserRoleLabels } from '../../lib/user-role-format';
import { useTheme } from '../../theme/useTheme';
import { createStyles } from '../../theme/createStyles';
import type { ThemeTokens } from '../../theme/tokens';

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

const LOADING_LABEL = '불러오는 중...';
const LOAD_FAILED_LABEL = '정보를 불러오지 못했어요';

/**
 * 표시값 결정 — 서버 프로필이 기준이고, 조회 전/실패 시에만 토큰 값이나 상태 문구로 대체한다.
 * accessToken은 메모리에만 있어 앱을 새로 열면 비므로 토큰만으로는 내 정보를 보여줄 수 없다.
 */
function resolveProfileField(
  serverValue: string | undefined,
  tokenValue: string | undefined,
  isLoading: boolean,
  isError: boolean
): string {
  if (serverValue !== undefined) return serverValue;
  if (tokenValue !== undefined) return tokenValue;
  if (isLoading) return LOADING_LABEL;
  if (isError) return LOAD_FAILED_LABEL;
  return '-';
}

export default function MeScreen() {
  const router = useRouter();
  const accessToken = useAuthStore((s) => s.accessToken);
  const logout = useAuthStore((s) => s.logout);
  const isOrdersUnifiedEnabled = isFeatureEnabled('orders.unified.enabled');
  const { tokens } = useTheme();
  const styles = useStyles(tokens);

  const payload = decodeJwt(accessToken);
  const { data: profile, isLoading, isError } = useMyProfile();

  const emailText = resolveProfileField(profile?.email, payload?.email, isLoading, isError);
  const userIdText = resolveProfileField(
    profile?.id !== undefined ? String(profile.id) : undefined,
    payload?.sub,
    isLoading,
    isError
  );

  async function handleLogout() {
    await logout();
    router.replace('/(auth)/login');
  }

  return (
    <View style={styles.container} accessibilityLabel="마이페이지 화면">
      <Text style={styles.title}>마이페이지</Text>

      <View style={styles.card}>
        <Text style={styles.label}>이메일</Text>
        <Text style={styles.value}>{emailText}</Text>

        <Text style={[styles.label, styles.mt]}>사용자 ID</Text>
        <Text style={styles.value}>{userIdText}</Text>

        <Text style={[styles.label, styles.mt]}>역할</Text>
        <Text style={styles.value}>{formatUserRoleLabels(payload?.roles)}</Text>
      </View>

      {isOrdersUnifiedEnabled ? (
        <View style={styles.entryPoint}>
          <ListItem title="내 주문 내역" onPress={() => router.push(ROUTES.orders)} />
        </View>
      ) : null}

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

const useStyles = createStyles((theme: ThemeTokens) =>
  StyleSheet.create({
    entryPoint: { marginBottom: 16 },
    container: { flex: 1, backgroundColor: theme.background, padding: 24, paddingTop: 64 },
    title: { fontSize: 28, fontWeight: 'bold', color: theme.textPrimary, marginBottom: 24 },
    card: {
      borderWidth: 1,
      borderColor: theme.border,
      borderRadius: 12,
      padding: 16,
      marginBottom: 24,
    },
    label: { fontSize: 12, color: theme.textMuted },
    value: { fontSize: 16, color: theme.textPrimary, marginTop: 2 },
    mt: { marginTop: 14 },
    logoutButton: {
      borderWidth: 1,
      borderColor: theme.danger,
      borderRadius: 8,
      paddingVertical: 14,
      alignItems: 'center',
    },
    logoutText: { color: theme.danger, fontSize: 16, fontWeight: '600' },
  })
);
