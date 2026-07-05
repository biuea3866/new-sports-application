/**
 * 시설 상세 화면 — MO-07
 * GET /facilities/{id} (public)
 */
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, StyleSheet } from 'react-native';
import { useLocalSearchParams, router } from 'expo-router';
import { useFacilityDetail } from '../../../lib/useFacility';
import type { FacilityType } from '../../../api/types';

const TYPE_LABEL: Record<FacilityType, string> = {
  INDOOR: '실내',
  OUTDOOR: '실외',
  MIXED: '복합',
};

export default function FacilityDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const facilityId = id ?? '';

  const { data, isLoading, isError, error } = useFacilityDetail(facilityId);

  return (
    <View style={styles.container}>
      <TouchableOpacity
        style={styles.backButton}
        onPress={() => router.back()}
        accessible={true}
        accessibilityLabel="뒤로 가기"
        accessibilityRole="button"
      >
        <Text style={styles.backText}>{'< 뒤로'}</Text>
      </TouchableOpacity>

      {isLoading && (
        <View style={styles.centerBox} accessible={true} accessibilityLabel="로딩 중">
          <ActivityIndicator size="large" color="#007AFF" />
        </View>
      )}

      {isError && (
        <View style={styles.centerBox} accessible={true} accessibilityLabel="오류 발생">
          <Text style={styles.errorText}>
            {error instanceof Error ? error.message : '시설 정보를 불러오지 못했습니다.'}
          </Text>
        </View>
      )}

      {!isLoading && !isError && data !== undefined && (
        <>
          <ScrollView
            contentContainerStyle={styles.scrollContent}
            accessible={false}
            accessibilityLabel="시설 상세 정보"
          >
            <Text style={styles.name}>{data.name}</Text>
            <View style={styles.row}>
              <Text style={styles.label}>구</Text>
              <Text style={styles.value}>{data.gu}</Text>
            </View>
            <View style={styles.row}>
              <Text style={styles.label}>타입</Text>
              <Text style={styles.value}>{TYPE_LABEL[data.type]}</Text>
            </View>
            <View style={styles.row}>
              <Text style={styles.label}>주소</Text>
              <Text style={styles.value}>{data.address}</Text>
            </View>
            <View style={styles.row}>
              <Text style={styles.label}>주차</Text>
              <Text style={styles.value}>{data.parking ? '가능' : '불가'}</Text>
            </View>
            {data.tel.length > 0 && (
              <View style={styles.row}>
                <Text style={styles.label}>전화</Text>
                <Text style={[styles.value, styles.phone]}>{data.tel}</Text>
              </View>
            )}
          </ScrollView>
          <View style={styles.bookingButtonContainer}>
            <TouchableOpacity
              style={styles.bookingButton}
              onPress={() => router.push(`/booking/new?facilityId=${facilityId}`)}
              accessibilityRole="button"
              accessibilityLabel="예약하기"
            >
              <Text style={styles.bookingButtonText}>예약하기</Text>
            </TouchableOpacity>
          </View>
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F2F2F7',
  },
  backButton: {
    paddingHorizontal: 16,
    paddingTop: 56,
    paddingBottom: 12,
    backgroundColor: '#fff',
  },
  backText: {
    fontSize: 16,
    color: '#007AFF',
  },
  centerBox: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  errorText: {
    fontSize: 14,
    color: '#FF3B30',
    textAlign: 'center',
  },
  scrollContent: {
    padding: 16,
  },
  name: {
    fontSize: 22,
    fontWeight: '700',
    color: '#1C1C1E',
    marginBottom: 20,
  },
  row: {
    flexDirection: 'row',
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#C7C7CC',
  },
  label: {
    width: 56,
    fontSize: 14,
    color: '#8E8E93',
  },
  value: {
    flex: 1,
    fontSize: 14,
    color: '#1C1C1E',
  },
  phone: {
    color: '#007AFF',
  },
  bookingButtonContainer: {
    padding: 16,
    backgroundColor: '#fff',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#C7C7CC',
  },
  bookingButton: {
    backgroundColor: '#007AFF',
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
  },
  bookingButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '700',
  },
});
