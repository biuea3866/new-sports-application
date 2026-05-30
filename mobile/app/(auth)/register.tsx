/**
 * 회원가입 화면
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
import { register, login } from '../../api/auth';
import { useAuthStore } from '../../lib/auth';

interface FormState {
  email: string;
  password: string;
}

export default function RegisterScreen() {
  const [form, setForm] = useState<FormState>({ email: '', password: '' });
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const setTokens = useAuthStore((s) => s.setTokens);

  const handleRegister = async () => {
    if (!form.email || !form.password) {
      setErrorMessage('이메일과 비밀번호를 입력해주세요.');
      return;
    }
    if (form.password.length < 8) {
      setErrorMessage('비밀번호는 8자 이상이어야 합니다.');
      return;
    }
    setErrorMessage(null);
    setIsLoading(true);
    try {
      await register({ email: form.email, password: form.password });
      // 회원가입 후 자동 로그인
      const loginRes = await login({ email: form.email, password: form.password });
      await setTokens({ accessToken: loginRes.accessToken, refreshToken: loginRes.refreshToken });
      router.replace('/(tabs)/');
    } catch {
      setErrorMessage('회원가입에 실패했습니다. 이미 사용 중인 이메일일 수 있습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.flex}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={styles.container} accessible={true} accessibilityLabel="회원가입 화면">
        <Text style={styles.title}>회원가입</Text>

        <View style={styles.fieldGroup}>
          <Text nativeID="regEmailLabel" style={styles.label} accessibilityRole="text">
            이메일
          </Text>
          <TextInput
            style={styles.input}
            accessibilityLabel="이메일 입력"
            accessibilityLabelledBy="regEmailLabel"
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
          <Text nativeID="regPasswordLabel" style={styles.label} accessibilityRole="text">
            비밀번호 (8자 이상)
          </Text>
          <TextInput
            style={styles.input}
            accessibilityLabel="비밀번호 입력"
            accessibilityLabelledBy="regPasswordLabel"
            secureTextEntry
            value={form.password}
            onChangeText={(v) => setForm((prev) => ({ ...prev, password: v }))}
            placeholder="비밀번호 (8자 이상)"
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
          onPress={handleRegister}
          disabled={isLoading}
          accessibilityRole="button"
          accessibilityLabel="회원가입"
          accessibilityState={{ disabled: isLoading }}
        >
          {isLoading ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.buttonText}>회원가입</Text>
          )}
        </Pressable>

        <Pressable
          style={styles.linkButton}
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="로그인 화면으로 돌아가기"
        >
          <Text style={styles.linkText}>이미 계정이 있으신가요? 로그인</Text>
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
