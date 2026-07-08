/**
 * 예약 신청 화면 — MO-04
 * Query: ?facilityId=&programId=
 *
 * 1. GET /facilities/{facilityId}/slots 슬롯 목록 조회 (programId 지정 시 해당 program 회차만)
 * 2. 슬롯 선택 + 결제수단 선택
 * 3. POST /bookings → 응답 bookingId 수신
 * 4. /payment?orderType=BOOKING&orderId={bookingId}&amount={amount}&method={method} 진입
 *
 * NOTE: SlotResponse에 price 필드가 없어 일반 예약(programId 미지정)은 amount 10000(원)
 * 고정을 유지한다(design-fe-app.md "확인 필요" — Slot에 price가 추가되면 교체).
 *
 * FE-28(A-F2): programId 지정 시 program.price를 결제 금액으로 사용한다
 * (design-fe-app.md "결제 흐름 재사용 결정" 인접 근거, "API 연동 표" A-F2). program 가격을
 * 아직 확보하지 못했으면(목록 로딩 중이거나 매칭 실패) 예약 버튼을 비활성화해, 잘못된
 * 고정 금액으로 결제가 개시되지 않도록 막는다. programId가 없으면 기존 흐름을 그대로
 * 유지한다(하위호환).
 *
 * 대기질 경고·확인 게이트 (FE-16, 근거: design-fe-app.md FR-13/FR-14)
 * - 시설 좌표(GET /facilities/{id})로 대기질(FE-12)을 조회해 BAD 이상이면
 *   슬롯 섹션 위에 AirQualityWarning(FE-14) 배너를 노출한다.
 * - BAD 이상이고 아직 미확인이면 예약 버튼 라벨이 "확인하고 예약"으로 바뀌고,
 *   첫 탭은 확인(confirmed=true)만 하고 예약을 실행하지 않는다. 두 번째 탭에서 실행된다.
 * - 좌표 미확보·대기질 조회 실패·UNKNOWN 등급은 경고 없이 기존 흐름대로 예약을 바로 진행한다
 *   (대기질 조회 실패가 예약을 막지 않는다).
 */
import { useState } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  StyleSheet,
} from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useSlots, useCreateBooking } from '../../lib/useBooking';
import { useFacilityDetail } from '../../lib/useFacility';
import { useAirQuality } from '../../lib/useAirQuality';
import { usePrograms } from '../../lib/useProgram';
import { AirQualityWarning } from '../../components/AirQualityWarning';
import { isBadOrWorse } from '../../lib/air-quality-format';
import type { PaymentMethod, SlotResponse } from '../../api/types';

const PAYMENT_METHODS: { method: PaymentMethod; label: string }[] = [
  { method: 'CREDIT_CARD', label: '신용카드' },
  { method: 'KAKAO', label: '카카오페이' },
  { method: 'TOSS', label: '토스페이' },
  { method: 'NAVER', label: '네이버페이' },
  { method: 'BANK_TRANSFER', label: '계좌이체' },
];

const BOOKING_AMOUNT = 10000; // TODO: SlotResponse에 price 추가 시 교체

interface SlotItemProps {
  slot: SlotResponse;
  isSelected: boolean;
  onSelect: () => void;
}

function SlotItem({ slot, isSelected, onSelect }: SlotItemProps) {
  const dateStr = new Date(slot.date).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });

  return (
    <TouchableOpacity
      style={[styles.slotCard, isSelected && styles.slotCardSelected]}
      onPress={onSelect}
      accessibilityRole="radio"
      accessibilityLabel={`슬롯 ${slot.timeRange} ${dateStr}`}
      accessibilityState={{ selected: isSelected }}
    >
      <Text style={[styles.slotTimeRange, isSelected && styles.slotTimeRangeSelected]}>
        {slot.timeRange}
      </Text>
      <Text style={styles.slotDate}>{dateStr}</Text>
      <Text style={styles.slotCapacity}>정원 {slot.capacity}명</Text>
    </TouchableOpacity>
  );
}

export default function BookingNewScreen() {
  const { facilityId, programId } = useLocalSearchParams<{
    facilityId: string;
    programId?: string;
  }>();
  const router = useRouter();

  const resolvedFacilityId = facilityId ?? '';
  const resolvedProgramId =
    typeof programId === 'string' && programId.length > 0 ? Number(programId) : undefined;
  const isProgramBooking = resolvedProgramId !== undefined && !Number.isNaN(resolvedProgramId);

  const { data: slots, isLoading, isError } = useSlots(resolvedFacilityId, resolvedProgramId);
  const { mutate: createBooking, isPending } = useCreateBooking();

  // program 예약(A-F2)은 program.price를 결제 금액으로 쓴다. usePrograms(facilityId) 전체
  // 목록에서 programId로 매칭한다(program 단건 조회 API가 없어 목록 조회를 재사용한다).
  const { data: programs, isLoading: isProgramsLoading } = usePrograms(
    isProgramBooking ? resolvedFacilityId : ''
  );
  const selectedProgram = isProgramBooking
    ? programs?.find((program) => program.id === resolvedProgramId)
    : undefined;
  const isProgramPriceReady = !isProgramBooking || selectedProgram !== undefined;
  const bookingAmount = isProgramBooking ? (selectedProgram?.price ?? 0) : BOOKING_AMOUNT;

  const { data: facility } = useFacilityDetail(resolvedFacilityId);
  const facilityLat = facility?.lat ?? null;
  const facilityLng = facility?.lng ?? null;
  const { data: airQuality, isSuccess: isAirQualitySuccess } = useAirQuality(
    facilityLat,
    facilityLng
  );
  const needsAirQualityConfirmation =
    isAirQualitySuccess && airQuality !== undefined && isBadOrWorse(airQuality.representativeGrade);

  const [selectedSlotId, setSelectedSlotId] = useState<number | null>(null);
  const [selectedMethod, setSelectedMethod] = useState<PaymentMethod>('CREDIT_CARD');
  const [isAirQualityConfirmed, setIsAirQualityConfirmed] = useState(false);

  const handleBook = () => {
    if (selectedSlotId === null) {
      Alert.alert('안내', '슬롯을 선택해주세요.');
      return;
    }

    if (!isProgramPriceReady) {
      Alert.alert('안내', '상품 가격을 확인하는 중입니다. 잠시 후 다시 시도해주세요.');
      return;
    }

    if (needsAirQualityConfirmation && !isAirQualityConfirmed) {
      setIsAirQualityConfirmed(true);
      return;
    }

    createBooking(
      {
        slotId: selectedSlotId,
        paymentMethod: selectedMethod,
        amount: bookingAmount,
        currency: 'KRW',
      },
      {
        onSuccess: (result) => {
          router.push(
            `/payment?orderType=BOOKING&orderId=${result.bookingId}&amount=${bookingAmount}&method=${selectedMethod}`
          );
        },
        onError: () => {
          Alert.alert('오류', '예약 생성에 실패했습니다. 다시 시도해주세요.');
        },
      }
    );
  };

  if (resolvedFacilityId.length === 0) {
    return (
      <View style={styles.centered} accessibilityLabel="잘못된 접근">
        <Text style={styles.errorText} accessibilityRole="alert">
          시설 정보가 없습니다.
        </Text>
      </View>
    );
  }

  if (isLoading) {
    return (
      <View style={styles.centered} accessibilityLabel="슬롯 목록 로딩 중">
        <ActivityIndicator size="large" color="#007AFF" />
      </View>
    );
  }

  if (isError) {
    return (
      <View style={styles.centered} accessibilityLabel="슬롯 목록 오류">
        <Text style={styles.errorText} accessibilityRole="alert">
          슬롯 목록을 불러오지 못했습니다.
        </Text>
      </View>
    );
  }

  const availableSlots = slots ?? [];
  const isProgramPricePending = isProgramBooking && !isProgramPriceReady && isProgramsLoading;
  const bookButtonLabel = isProgramPricePending
    ? '가격 확인 중'
    : needsAirQualityConfirmation && !isAirQualityConfirmed
      ? '확인하고 예약'
      : isPending
        ? '예약 중...'
        : '예약 진행';
  const isBookDisabled = selectedSlotId === null || isPending || !isProgramPriceReady;

  return (
    <View style={styles.container} accessible={false} accessibilityLabel="예약 신청 화면">
      <Text style={styles.title} accessibilityRole="header">
        예약 신청
      </Text>

      {needsAirQualityConfirmation && airQuality !== undefined && (
        <View style={styles.airQualityWarningWrapper}>
          <AirQualityWarning grade={airQuality.representativeGrade} pm10={airQuality.pm10} />
        </View>
      )}

      <Text style={styles.sectionLabel}>슬롯 선택</Text>
      {availableSlots.length === 0 ? (
        <Text style={styles.emptyText} accessibilityRole="text">
          예약 가능한 슬롯이 없습니다.
        </Text>
      ) : (
        <FlatList<SlotResponse>
          data={availableSlots}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <SlotItem
              slot={item}
              isSelected={selectedSlotId === item.id}
              onSelect={() => setSelectedSlotId(item.id)}
            />
          )}
          style={styles.slotList}
          contentContainerStyle={styles.slotListContent}
        />
      )}

      <Text style={styles.sectionLabel}>결제수단</Text>
      <View style={styles.methodRow} accessibilityLabel="결제수단 목록">
        {PAYMENT_METHODS.map(({ method, label }) => {
          const isSelected = selectedMethod === method;
          return (
            <TouchableOpacity
              key={method}
              style={[styles.methodButton, isSelected && styles.methodButtonSelected]}
              onPress={() => setSelectedMethod(method)}
              accessibilityRole="radio"
              accessibilityLabel={label}
              accessibilityState={{ selected: isSelected }}
            >
              <Text style={[styles.methodLabel, isSelected && styles.methodLabelSelected]}>
                {label}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>

      <TouchableOpacity
        style={[styles.bookButton, isBookDisabled && styles.bookButtonDisabled]}
        onPress={handleBook}
        disabled={isBookDisabled}
        accessibilityRole="button"
        accessibilityLabel={bookButtonLabel}
        accessibilityState={{ disabled: isBookDisabled }}
      >
        <Text style={styles.bookButtonText}>{bookButtonLabel}</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  airQualityWarningWrapper: {
    marginBottom: 20,
  },
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
    marginBottom: 24,
  },
  sectionLabel: {
    fontSize: 15,
    fontWeight: '600',
    color: '#3C3C43',
    marginBottom: 8,
  },
  slotList: {
    maxHeight: 240,
    marginBottom: 20,
  },
  slotListContent: {
    gap: 8,
  },
  slotCard: {
    borderRadius: 10,
    borderWidth: 1.5,
    borderColor: '#E5E5EA',
    padding: 14,
    backgroundColor: '#F2F2F7',
  },
  slotCardSelected: {
    borderColor: '#007AFF',
    backgroundColor: '#EAF4FF',
  },
  slotTimeRange: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1C1C1E',
    marginBottom: 2,
  },
  slotTimeRangeSelected: {
    color: '#007AFF',
  },
  slotDate: {
    fontSize: 13,
    color: '#8E8E93',
    marginBottom: 2,
  },
  slotCapacity: {
    fontSize: 12,
    color: '#8E8E93',
  },
  methodRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 32,
  },
  methodButton: {
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderRadius: 8,
    borderWidth: 1.5,
    borderColor: '#E5E5EA',
    backgroundColor: '#F2F2F7',
  },
  methodButtonSelected: {
    borderColor: '#007AFF',
    backgroundColor: '#EAF4FF',
  },
  methodLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: '#3C3C43',
  },
  methodLabelSelected: {
    color: '#007AFF',
  },
  bookButton: {
    backgroundColor: '#007AFF',
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
    marginBottom: 40,
  },
  bookButtonDisabled: {
    backgroundColor: '#9E9E9E',
  },
  bookButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '700',
  },
  emptyText: {
    fontSize: 14,
    color: '#8E8E93',
    marginBottom: 20,
  },
  errorText: {
    color: '#FF3B30',
    fontSize: 15,
  },
});
