/**
 * 로그인 화면
 * 이메일/비밀번호로 POST /auth/login → 토큰 저장 → 홈 탭으로 이동.
 */
import { useState } from 'react';
import { View, Text, TextInput, Pressable, StyleSheet, ActivityIndicator } from 'react-native';
import { useRouter, Link } from 'expo-router';
import { AxiosError } from 'axios';
import { getBeClient } from '../../api/be-client';
import { useAuthStore } from '../../lib/auth';

interface LoginResponse {
  accessToken: string;
  refreshToken: string;
}

export default function LoginScreen() {
  const router = useRouter();
  const setTokens = useAuthStore((s) => s.setTokens);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleLogin() {
    if (!email.trim() || !password) {
      setError('이메일과 비밀번호를 입력해 주세요.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const res = await getBeClient().post<LoginResponse>('/auth/login', {
        email: email.trim(),
        password,
      });
      await setTokens({
        accessToken: res.data.accessToken,
        refreshToken: res.data.refreshToken,
      });
      router.replace('/(tabs)');
    } catch (e) {
      if (e instanceof AxiosError && e.response?.status === 401) {
        setError('이메일 또는 비밀번호가 올바르지 않습니다.');
      } else {
        setError('로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <View style={styles.container} accessibilityLabel="로그인 화면">
      <Text style={styles.title}>로그인</Text>
      <Text style={styles.subtitle}>Sports App에 오신 것을 환영합니다</Text>

      {error !== null && (
        <Text style={styles.error} accessibilityRole="alert">
          {error}
        </Text>
      )}

      <TextInput
        style={styles.input}
        placeholder="이메일"
        value={email}
        onChangeText={setEmail}
        autoCapitalize="none"
        keyboardType="email-address"
        autoComplete="email"
        accessibilityLabel="이메일 입력"
      />
      <TextInput
        style={styles.input}
        placeholder="비밀번호"
        value={password}
        onChangeText={setPassword}
        secureTextEntry
        accessibilityLabel="비밀번호 입력"
      />

      <Pressable
        style={[styles.button, submitting && styles.buttonDisabled]}
        onPress={() => void handleLogin()}
        disabled={submitting}
        accessibilityRole="button"
        accessibilityLabel="로그인"
      >
        {submitting ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.buttonText}>로그인</Text>
        )}
      </Pressable>

      <View style={styles.footer}>
        <Text style={styles.footerText}>계정이 없으신가요? </Text>
        <Link href="/(auth)/register" style={styles.link}>
          회원가입
        </Link>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff', padding: 24, justifyContent: 'center' },
  title: { fontSize: 28, fontWeight: 'bold', color: '#1C1C1E', marginBottom: 4 },
  subtitle: { fontSize: 14, color: '#8E8E93', marginBottom: 28 },
  input: {
    borderWidth: 1,
    borderColor: '#D1D1D6',
    borderRadius: 8,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 16,
    marginBottom: 12,
  },
  button: {
    backgroundColor: '#007AFF',
    borderRadius: 8,
    paddingVertical: 14,
    alignItems: 'center',
    marginTop: 8,
  },
  buttonDisabled: { opacity: 0.6 },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '600' },
  error: { color: '#FF3B30', fontSize: 13, marginBottom: 12 },
  footer: { flexDirection: 'row', justifyContent: 'center', marginTop: 20 },
  footerText: { color: '#8E8E93', fontSize: 14 },
  link: { color: '#007AFF', fontSize: 14, fontWeight: '600' },
});
