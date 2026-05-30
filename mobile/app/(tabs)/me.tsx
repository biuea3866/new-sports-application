/**
 * 마이페이지 탭
 */
import { View, Text, Pressable, StyleSheet, ActivityIndicator } from 'react-native';
import { router } from 'expo-router';
import { useMyProfile } from '../../lib/useMyProfile';
import { useAuthStore } from '../../lib/auth';

export default function MeScreen() {
  const { data: profile, isLoading, isError } = useMyProfile();
  const logout = useAuthStore((s) => s.logout);

  const handleLogout = async () => {
    await logout();
    router.replace('/(auth)/login');
  };

  return (
    <View style={styles.container} accessible={true} accessibilityLabel="마이페이지 화면">
      <Text style={styles.title}>마이페이지</Text>

      {isLoading && (
        <ActivityIndicator
          size="large"
          color="#007AFF"
          accessibilityLabel="프로필 로딩 중"
        />
      )}

      {isError && (
        <Text style={styles.errorText} accessibilityRole="alert">
          프로필을 불러오지 못했습니다.
        </Text>
      )}

      {profile !== undefined && (
        <View style={styles.profileCard} accessibilityLabel="프로필 정보">
          <Text style={styles.emailText} accessibilityRole="text">
            {profile.email}
          </Text>
          <Text style={styles.statusText} accessibilityRole="text">
            상태: {profile.status}
          </Text>
        </View>
      )}

      <Pressable
        style={styles.logoutButton}
        onPress={handleLogout}
        accessibilityRole="button"
        accessibilityLabel="로그아웃"
      >
        <Text style={styles.logoutButtonText}>로그아웃</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    paddingHorizontal: 24,
    paddingTop: 60,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1C1C1E',
    marginBottom: 32,
  },
  profileCard: {
    backgroundColor: '#F2F2F7',
    borderRadius: 12,
    padding: 16,
    marginBottom: 24,
  },
  emailText: {
    fontSize: 16,
    color: '#1C1C1E',
    marginBottom: 8,
  },
  statusText: {
    fontSize: 14,
    color: '#3C3C43',
  },
  errorText: {
    color: '#FF3B30',
    fontSize: 14,
    marginBottom: 16,
  },
  logoutButton: {
    height: 50,
    backgroundColor: '#FF3B30',
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 'auto',
    marginBottom: 40,
  },
  logoutButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
});
