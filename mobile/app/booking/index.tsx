/**
 * 예약 목록 화면 — 내 예약 조회 + 취소
 */
import {
  View,
  Text,
  FlatList,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { useMyBookings, useCancelBooking } from '../../lib/useMyBookings';
import type { BookingResponse } from '../../api/types';

function BookingItem({
  booking,
  onCancel,
  isCancelling,
}: {
  booking: BookingResponse;
  onCancel: (id: number) => void;
  isCancelling: boolean;
}) {
  const canCancel = booking.status === 'PENDING' || booking.status === 'CONFIRMED';

  return (
    <View style={styles.card} accessibilityLabel={`예약 ${booking.id}`}>
      <Text style={styles.bookingId} accessibilityRole="text">
        예약 #{booking.id}
      </Text>
      <Text style={styles.bookingStatus} accessibilityRole="text">
        상태: {booking.status}
      </Text>
      <Text style={styles.bookingDate} accessibilityRole="text">
        예약일: {new Date(booking.createdAt).toLocaleDateString('ko-KR')}
      </Text>

      {canCancel && (
        <Pressable
          style={[styles.cancelButton, isCancelling && styles.cancelButtonDisabled]}
          onPress={() => onCancel(booking.id)}
          disabled={isCancelling}
          accessibilityRole="button"
          accessibilityLabel={`예약 ${booking.id} 취소`}
          accessibilityState={{ disabled: isCancelling }}
        >
          <Text style={styles.cancelButtonText}>
            {isCancelling ? '취소 중...' : '예약 취소'}
          </Text>
        </Pressable>
      )}
    </View>
  );
}

export default function BookingListScreen() {
  const { data, isLoading, isError } = useMyBookings();
  const { mutate: cancelBooking, isPending } = useCancelBooking();

  const handleCancel = (id: number) => {
    Alert.alert('예약 취소', '이 예약을 취소하시겠습니까?', [
      { text: '아니오', style: 'cancel' },
      {
        text: '취소하기',
        style: 'destructive',
        onPress: () =>
          cancelBooking(
            { id },
            {
              onError: () => {
                Alert.alert('오류', '예약 취소에 실패했습니다.');
              },
            }
          ),
      },
    ]);
  };

  if (isLoading) {
    return (
      <View style={styles.centered} accessibilityLabel="예약 목록 로딩 중">
        <ActivityIndicator size="large" color="#007AFF" />
      </View>
    );
  }

  if (isError) {
    return (
      <View style={styles.centered} accessibilityLabel="예약 목록 오류">
        <Text style={styles.errorText} accessibilityRole="alert">
          예약 목록을 불러오지 못했습니다.
        </Text>
      </View>
    );
  }

  const bookings = data?.bookings ?? [];

  return (
    <View style={styles.container} accessible={true} accessibilityLabel="예약 목록 화면">
      <Text style={styles.title}>예약 목록</Text>
      {bookings.length === 0 ? (
        <Text style={styles.emptyText} accessibilityRole="text">
          예약 내역이 없습니다.
        </Text>
      ) : (
        <FlatList
          data={bookings}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <BookingItem
              booking={item}
              onCancel={handleCancel}
              isCancelling={isPending}
            />
          )}
          contentContainerStyle={styles.list}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    paddingHorizontal: 16,
    paddingTop: 60,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#fff',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1C1C1E',
    marginBottom: 20,
  },
  list: {
    paddingBottom: 40,
  },
  card: {
    backgroundColor: '#F2F2F7',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
  },
  bookingId: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1C1C1E',
    marginBottom: 4,
  },
  bookingStatus: {
    fontSize: 14,
    color: '#3C3C43',
    marginBottom: 4,
  },
  bookingDate: {
    fontSize: 13,
    color: '#8E8E93',
    marginBottom: 12,
  },
  cancelButton: {
    backgroundColor: '#FF3B30',
    borderRadius: 8,
    paddingVertical: 10,
    alignItems: 'center',
  },
  cancelButtonDisabled: {
    backgroundColor: '#9E9E9E',
  },
  cancelButtonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
  },
  emptyText: {
    fontSize: 15,
    color: '#8E8E93',
    textAlign: 'center',
    marginTop: 40,
  },
  errorText: {
    color: '#FF3B30',
    fontSize: 15,
  },
});
