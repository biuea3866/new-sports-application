/**
 * 마이페이지 탭
 */
import { View, Text, Pressable, StyleSheet, ActivityIndicator, ScrollView } from 'react-native';
import { router } from 'expo-router';
import { useMyProfile } from '../../lib/useMyProfile';
import { useAuthStore } from '../../lib/auth';
import { ROUTES } from '../../lib/navigation';

interface MenuItemProps {
  label: string;
  onPress: () => void;
}

function MenuItem({ label, onPress }: MenuItemProps) {
  return (
    <Pressable
      style={styles.menuItem}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
    >
      <Text style={styles.menuItemText}>{label}</Text>
      <Text style={styles.menuItemChevron} accessibilityElementsHidden>›</Text>
    </Pressable>
  );
}

export default function MeScreen() {
  const { data: profile, isLoading, isError } = useMyProfile();
  const logout = useAuthStore((s) => s.logout);

  const handleLogout = async () => {
    await logout();
    router.replace('/(auth)/login');
  };

  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.container}
      accessible={true}
      accessibilityLabel="마이페이지 화면"
    >
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
          <Text style={styles.createdAtText} accessibilityRole="text">
            가입일: {new Date(profile.createdAt).toLocaleDateString('ko-KR')}
          </Text>
        </View>
      )}

      <View style={styles.menuSection} accessibilityLabel="거래 내역 메뉴">
        <Text style={styles.menuSectionTitle}>거래 내역</Text>
        <MenuItem
          label="내 예약"
          onPress={() => router.push(ROUTES.booking.list)}
        />
        <MenuItem
          label="내 주문"
          onPress={() => router.push(ROUTES.order.list)}
        />
        <MenuItem
          label="내 티켓"
          onPress={() => router.push(ROUTES.tabs.tickets)}
        />
        <MenuItem
          label="결제 내역"
          onPress={() => router.push(ROUTES.payment.list)}
        />
      </View>

      <Pressable
        style={styles.logoutButton}
        onPress={handleLogout}
        accessibilityRole="button"
        accessibilityLabel="로그아웃"
      >
        <Text style={styles.logoutButtonText}>로그아웃</Text>
      </Pressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: {
    flex: 1,
    backgroundColor: '#fff',
  },
  container: {
    paddingHorizontal: 24,
    paddingTop: 60,
    paddingBottom: 40,
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
    marginBottom: 4,
  },
  createdAtText: {
    fontSize: 13,
    color: '#8E8E93',
  },
  errorText: {
    color: '#FF3B30',
    fontSize: 14,
    marginBottom: 16,
  },
  menuSection: {
    marginBottom: 32,
  },
  menuSectionTitle: {
    fontSize: 13,
    fontWeight: '600',
    color: '#8E8E93',
    marginBottom: 8,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  menuItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#F2F2F7',
    borderRadius: 10,
    paddingHorizontal: 16,
    paddingVertical: 14,
    marginBottom: 8,
  },
  menuItemText: {
    fontSize: 16,
    color: '#1C1C1E',
  },
  menuItemChevron: {
    fontSize: 20,
    color: '#C7C7CC',
  },
  logoutButton: {
    height: 50,
    backgroundColor: '#FF3B30',
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 40,
  },
  logoutButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
});
