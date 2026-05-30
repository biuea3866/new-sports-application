/**
 * 로그인 화면
 */
import {
  View,
  Text,
  TextInput,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { useState } from 'react';
import { router } from 'expo-router';
import { login } from '../../api/auth';
import { useAuthStore } from '../../lib/auth';

interface FormState {
  email: string;
  password: string;
}

export default function LoginScreen() {
  const [form, setForm] = useState<FormState>({ email: '', password: '' });
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const setTokens = useAuthStore((s) => s.setTokens);

  const handleLogin = async () => {
    if (!form.email || !form.password) {
      setErrorMessage('이메일과 비밀번호를 입력해주세요.');
      return;
    }
    setErrorMessage(null);
    setIsLoading(true);
    try {
      const res = await login({ email: form.email, password: form.password });
      await setTokens({ accessToken: res.accessToken, refreshToken: res.refreshToken });
      router.replace('/(tabs)/');
    } catch {
      setErrorMessage('이메일 또는 비밀번호가 올바르지 않습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.flex}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={styles.container} accessible={true} accessibilityLabel="로그인 화면">
        <Text style={styles.title}>로그인</Text>

        <View style={styles.fieldGroup}>
          <Text nativeID="emailLabel" style={styles.label} accessibilityRole="text">
            이메일
          </Text>
          <TextInput
            style={styles.input}
            accessibilityLabel="이메일 입력"
            accessibilityLabelledBy="emailLabel"
            keyboardType="email-address"
            autoCapitalize="none"
            autoCorrect={false}
            value={form.email}
            onChangeText={(v) => setForm((prev) => ({ ...prev, email: v }))}
            placeholder="example@email.com"
            placeholderTextColor="#8E8E93"
            editable={!isLoading}
          />
        </View>

        <View style={styles.fieldGroup}>
          <Text nativeID="passwordLabel" style={styles.label} accessibilityRole="text">
            비밀번호
          </Text>
          <TextInput
            style={styles.input}
            accessibilityLabel="비밀번호 입력"
            accessibilityLabelledBy="passwordLabel"
            secureTextEntry
            value={form.password}
            onChangeText={(v) => setForm((prev) => ({ ...prev, password: v }))}
            placeholder="비밀번호"
            placeholderTextColor="#8E8E93"
            editable={!isLoading}
          />
        </View>

        {errorMessage !== null && (
          <Text
            style={styles.errorText}
            accessibilityRole="alert"
            accessibilityLiveRegion="polite"
          >
            {errorMessage}
          </Text>
        )}

        <Pressable
          style={[styles.button, isLoading && styles.buttonDisabled]}
          onPress={handleLogin}
          disabled={isLoading}
          accessibilityRole="button"
          accessibilityLabel="로그인"
          accessibilityState={{ disabled: isLoading }}
        >
          {isLoading ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.buttonText}>로그인</Text>
          )}
        </Pressable>

        <Pressable
          style={styles.linkButton}
          onPress={() => router.push('/(auth)/register')}
          accessibilityRole="button"
          accessibilityLabel="회원가입 화면으로 이동"
        >
          <Text style={styles.linkText}>계정이 없으신가요? 회원가입</Text>
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: {
    flex: 1,
    backgroundColor: '#fff',
  },
  container: {
    flex: 1,
    paddingHorizontal: 24,
    justifyContent: 'center',
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1C1C1E',
    marginBottom: 32,
  },
  fieldGroup: {
    marginBottom: 16,
  },
  label: {
    fontSize: 14,
    color: '#3C3C43',
    marginBottom: 6,
  },
  input: {
    height: 48,
    borderWidth: 1,
    borderColor: '#C7C7CC',
    borderRadius: 8,
    paddingHorizontal: 12,
    fontSize: 16,
    color: '#1C1C1E',
    backgroundColor: '#F2F2F7',
  },
  errorText: {
    color: '#FF3B30',
    fontSize: 14,
    marginBottom: 12,
  },
  button: {
    height: 50,
    backgroundColor: '#007AFF',
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 8,
  },
  buttonDisabled: {
    backgroundColor: '#9E9E9E',
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  linkButton: {
    marginTop: 20,
    alignItems: 'center',
  },
  linkText: {
    color: '#007AFF',
    fontSize: 14,
  },
});
