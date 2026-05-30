/**
 * 예약 신청 화면 — 시설/슬롯 확인 후 예약 생성
 * Query params: slotId, facilityName, date, startTime, endTime
 */
import {
  View,
  Text,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  Alert,
  ScrollView,
} from 'react-native';
import { useLocalSearchParams, router } from 'expo-router';
import { useCreateBooking } from '../../lib/useMyBookings';
import { ROUTES } from '../../lib/navigation';

export default function BookingNewScreen() {
  const params = useLocalSearchParams<Record<string, string>>();
  const slotId = params.slotId;
  const facilityName = params.facilityName;
  const date = params.date;
  const startTime = params.startTime;
  const endTime = params.endTime;

  const { mutate: createBooking, isPending } = useCreateBooking();

  const slotIdNumber = slotId !== undefined && slotId.length > 0 ? Number(slotId) : null;

  const handleConfirm = () => {
    if (slotIdNumber === null || isNaN(slotIdNumber)) {
      Alert.alert('오류', '슬롯 정보가 올바르지 않습니다.');
      return;
    }

    createBooking(
      { slotId: slotIdNumber },
      {
        onSuccess: () => {
          Alert.alert('예약 완료', '예약이 신청되었습니다.', [
            {
              text: '확인',
              onPress: () => router.replace(ROUTES.booking.list),
            },
          ]);
        },
        onError: () => {
          Alert.alert('오류', '예약에 실패했습니다. 다시 시도해주세요.');
        },
      }
    );
  };

  if (slotIdNumber === null || isNaN(slotIdNumber)) {
    return (
      <View style={styles.centered} accessible={true} accessibilityLabel="잘못된 접근">
        <Text style={styles.errorText} accessibilityRole="alert">
          슬롯 정보가 없습니다.
        </Text>
        <Pressable
          style={styles.backButton}
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="뒤로 가기"
        >
          <Text style={styles.backButtonText}>뒤로 가기</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      accessible={false}
      accessibilityLabel="예약 신청 화면"
    >
      <Pressable
        style={styles.navBack}
        onPress={() => router.back()}
        accessibilityRole="button"
        accessibilityLabel="뒤로 가기"
      >
        <Text style={styles.navBackText}>{'< 뒤로'}</Text>
      </Pressable>

      <Text style={styles.title}>예약 신청</Text>

      <View style={styles.card} accessible={true} accessibilityLabel="예약 정보">
        <Text style={styles.cardTitle}>예약 정보 확인</Text>

        {facilityName !== undefined && facilityName.length > 0 && (
          <View style={styles.row}>
            <Text style={styles.label}>시설</Text>
            <Text style={styles.value}>{facilityName}</Text>
          </View>
        )}

        {date !== undefined && date.length > 0 && (
          <View style={styles.row}>
            <Text style={styles.label}>날짜</Text>
            <Text style={styles.value}>{date}</Text>
          </View>
        )}

        {startTime !== undefined && startTime.length > 0 && endTime !== undefined && endTime.length > 0 && (
          <View style={styles.row}>
            <Text style={styles.label}>시간</Text>
            <Text style={styles.value}>
              {startTime} ~ {endTime}
            </Text>
          </View>
        )}

        <View style={styles.row}>
          <Text style={styles.label}>슬롯 ID</Text>
          <Text style={styles.value}>{slotIdNumber}</Text>
        </View>
      </View>

      <Pressable
        style={[styles.confirmButton, isPending && styles.confirmButtonDisabled]}
        onPress={handleConfirm}
        disabled={isPending}
        accessibilityRole="button"
        accessibilityLabel="예약 신청 확인"
        accessibilityState={{ disabled: isPending }}
      >
        {isPending ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.confirmButtonText}>예약 신청</Text>
        )}
      </Pressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  content: {
    paddingBottom: 40,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#fff',
    padding: 24,
  },
  navBack: {
    paddingHorizontal: 16,
    paddingTop: 56,
    paddingBottom: 12,
  },
  navBackText: {
    fontSize: 16,
    color: '#007AFF',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1C1C1E',
    paddingHorizontal: 16,
    marginBottom: 24,
  },
  card: {
    marginHorizontal: 16,
    backgroundColor: '#F2F2F7',
    borderRadius: 12,
    padding: 16,
    marginBottom: 32,
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1C1C1E',
    marginBottom: 16,
  },
  row: {
    flexDirection: 'row',
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#C7C7CC',
  },
  label: {
    width: 64,
    fontSize: 14,
    color: '#8E8E93',
  },
  value: {
    flex: 1,
    fontSize: 14,
    color: '#1C1C1E',
  },
  confirmButton: {
    marginHorizontal: 16,
    backgroundColor: '#007AFF',
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
  },
  confirmButtonDisabled: {
    backgroundColor: '#9E9E9E',
  },
  confirmButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  errorText: {
    fontSize: 15,
    color: '#FF3B30',
    marginBottom: 20,
    textAlign: 'center',
  },
  backButton: {
    paddingVertical: 12,
    paddingHorizontal: 24,
    backgroundColor: '#F2F2F7',
    borderRadius: 8,
  },
  backButtonText: {
    fontSize: 15,
    color: '#007AFF',
  },
});
