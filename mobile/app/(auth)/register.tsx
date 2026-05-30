/**
 * 회원가입 화면
 * POST /users/register → 자동 로그인(POST /auth/login) → 홈 탭으로 이동.
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

export default function RegisterScreen() {
  const router = useRouter();
  const setTokens = useAuthStore((s) => s.setTokens);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleRegister() {
    if (!email.trim() || !password) {
      setError('이메일과 비밀번호를 입력해 주세요.');
      return;
    }
    if (password.length < 8) {
      setError('비밀번호는 8자 이상이어야 합니다.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const client = getBeClient();
      await client.post('/users/register', { email: email.trim(), password });
      const res = await client.post<LoginResponse>('/auth/login', {
        email: email.trim(),
        password,
      });
      await setTokens({
        accessToken: res.data.accessToken,
        refreshToken: res.data.refreshToken,
      });
      router.replace('/(tabs)');
    } catch (e) {
      if (e instanceof AxiosError && e.response?.status === 409) {
        setError('이미 가입된 이메일입니다.');
      } else if (e instanceof AxiosError && e.response?.status === 422) {
        setError('입력값을 확인해 주세요. (이메일 형식 / 비밀번호 8자 이상)');
      } else {
        setError('회원가입에 실패했습니다. 잠시 후 다시 시도해 주세요.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <View style={styles.container} accessibilityLabel="회원가입 화면">
      <Text style={styles.title}>회원가입</Text>
      <Text style={styles.subtitle}>새 계정을 만들어 시작하세요</Text>

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
        placeholder="비밀번호 (8자 이상)"
        value={password}
        onChangeText={setPassword}
        secureTextEntry
        accessibilityLabel="비밀번호 입력"
      />

      <Pressable
        style={[styles.button, submitting && styles.buttonDisabled]}
        onPress={() => void handleRegister()}
        disabled={submitting}
        accessibilityRole="button"
        accessibilityLabel="회원가입"
      >
        {submitting ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.buttonText}>회원가입</Text>
        )}
      </Pressable>

      <View style={styles.footer}>
        <Text style={styles.footerText}>이미 계정이 있으신가요? </Text>
        <Link href="/(auth)/login" style={styles.link}>
          로그인
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
