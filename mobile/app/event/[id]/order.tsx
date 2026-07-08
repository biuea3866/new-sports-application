/**
 * 경기 좌석 선택 + 티켓 발권 화면
 *
 * 진입: /event/{id}/order?seatIds=1,2,3 (index.tsx에서 선택된 좌석 ID 전달)
 * 흐름:
 *   1. 선택된 좌석 목록 표시 (전달받은 seatIds 기반)
 *   2. POST /events/{id}/seats/select → lockId + expiresAt 획득 (선점)
 *   3. POST /ticket-orders → ticketOrderId + amount 획득 (주문 생성)
 *   4. router.push('/payment/new?orderType=TICKETING&orderId={ticketOrderId}&amount={amount}')
 *   5. 뒤로가기 또는 컴포넌트 언마운트 시 POST /events/{id}/seats/release (선점 해제)
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { useEvent } from '../../../lib/useEvent';
import { selectSeats, releaseSeats, purchaseTicketOrder } from '../../../api/ticketOrders';
import type { PaymentMethod, SeatInfo } from '../../../api/types';

type Phase = 'confirm' | 'selecting' | 'purchasing' | 'done';

function formatPrice(price: string): string {
  const num = parseFloat(price);
  return isNaN(num) ? price : `${num.toLocaleString('ko-KR')}원`;
}

function totalAmount(seats: SeatInfo[]): number {
  return seats.reduce((sum, seat) => sum + parseFloat(seat.price), 0);
}

interface SeatRowProps {
  seat: SeatInfo;
}

function SeatRow({ seat }: SeatRowProps) {
  return (
    <View
      style={styles.seatRow}
      accessible={true}
      accessibilityLabel={`${seat.section}구역 ${seat.rowNo}열 ${seat.seatNo}번 ${formatPrice(seat.price)}`}
    >
      <Text style={styles.seatLabel}>
        {seat.section}구역 {seat.rowNo}열 {seat.seatNo}번
      </Text>
      <Text style={styles.seatPrice}>{formatPrice(seat.price)}</Text>
    </View>
  );
}

export default function EventOrderScreen() {
  const { id, seatIds: seatIdsParam } = useLocalSearchParams<{
    id: string;
    seatIds: string;
  }>();

  const eventId = Number(id);
  const selectedSeatIds = useMemo<number[]>(() => {
    if (!seatIdsParam) return [];
    return seatIdsParam
      .split(',')
      .map((s) => parseInt(s.trim(), 10))
      .filter((n) => !isNaN(n) && n > 0);
  }, [seatIdsParam]);

  const { data: event } = useEvent(eventId);
  const [phase, setPhase] = useState<Phase>('confirm');
  const [lockId, setLockId] = useState<string | null>(null);
  const lockIdRef = useRef<string | null>(null);
  const releasedRef = useRef(false);

  const selectedSeats = useMemo<SeatInfo[]>(() => {
    if (!event?.seats) return [];
    return event.seats.filter((s) => selectedSeatIds.includes(s.id));
  }, [event, selectedSeatIds]);

  const amount = useMemo(() => totalAmount(selectedSeats), [selectedSeats]);

  // lockId 상태 변경 시 ref도 동기화 (클린업 함수에서 최신값 참조)
  useEffect(() => {
    lockIdRef.current = lockId;
  }, [lockId]);

  // 화면 이탈 시 선점 해제
  useEffect(() => {
    return () => {
      const currentLockId = lockIdRef.current;
      if (!currentLockId || releasedRef.current) return;
      releasedRef.current = true;
      void releaseSeats(eventId, selectedSeatIds);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleConfirm = useCallback(async () => {
    if (selectedSeatIds.length === 0) {
      Alert.alert('오류', '선택된 좌석이 없습니다.');
      return;
    }

    setPhase('selecting');
    let acquiredLockId: string | null = null;
    try {
      const result = await selectSeats(eventId, selectedSeatIds);
      acquiredLockId = result.lockId;
      setLockId(result.lockId);
      lockIdRef.current = result.lockId;
      setPhase('purchasing');

      const order = await purchaseTicketOrder({
        lockId: result.lockId,
        method: 'CREDIT_CARD' as PaymentMethod,
        currency: 'KRW',
      });

      releasedRef.current = true; // 주문 완료 후 release 방지 (BE가 이미 처리)
      router.push(
        `/payment/new?orderType=TICKETING&orderId=${order.ticketOrderId}&amount=${Math.round(amount)}&itemName=${encodeURIComponent(event?.title ?? '티켓')}`
      );
    } catch (error) {
      // purchase 실패 시 선점 해제 — 재시도 가능하도록 상태 초기화
      if (acquiredLockId !== null) {
        void releaseSeats(eventId, selectedSeatIds);
        setLockId(null);
        lockIdRef.current = null;
        releasedRef.current = false;
      }
      const message =
        error instanceof Error ? error.message : '처리에 실패했습니다. 다시 시도해주세요.';
      Alert.alert('오류', message);
      setPhase('confirm');
    }
  }, [eventId, selectedSeatIds, amount, event?.title]);

  const handleBack = useCallback(() => {
    router.back();
  }, []);

  if (selectedSeatIds.length === 0) {
    return (
      <View style={styles.container} accessible={true} accessibilityLabel="좌석 선택 화면">
        <Pressable
          style={styles.backButton}
          accessibilityRole="button"
          accessibilityLabel="뒤로 가기"
          onPress={handleBack}
        >
          <Text style={styles.backButtonText}>{'< 뒤로'}</Text>
        </Pressable>
        <View style={styles.center}>
          <Text style={styles.emptyText} accessibilityRole="text">
            선택된 좌석이 없습니다.
          </Text>
        </View>
      </View>
    );
  }

  if (phase === 'selecting' || phase === 'purchasing') {
    const loadingLabel = phase === 'selecting' ? '좌석 선점 중...' : '주문 생성 중...';
    return (
      <View style={styles.center} accessibilityLabel={loadingLabel}>
        <ActivityIndicator size="large" color="#007AFF" />
        <Text style={styles.loadingText}>{loadingLabel}</Text>
      </View>
    );
  }

  return (
    <View style={styles.container} accessible={false}>
      <Pressable
        style={styles.backButton}
        accessibilityRole="button"
        accessibilityLabel="뒤로 가기"
        onPress={handleBack}
      >
        <Text style={styles.backButtonText}>{'< 뒤로'}</Text>
      </Pressable>

      <ScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.title} accessibilityRole="header">
          선택 좌석 확인
        </Text>

        {event && (
          <Text style={styles.eventName} accessibilityRole="text">
            {event.title}
          </Text>
        )}

        <View style={styles.seatList} accessibilityLabel="선택한 좌석 목록">
          {selectedSeats.map((seat) => (
            <SeatRow key={seat.id} seat={seat} />
          ))}
        </View>

        <View style={styles.totalRow}>
          <Text style={styles.totalLabel}>합계</Text>
          <Text
            style={styles.totalAmount}
            accessibilityLabel={`합계 ${Math.round(amount).toLocaleString('ko-KR')}원`}
          >
            {Math.round(amount).toLocaleString('ko-KR')}원
          </Text>
        </View>

        <Text style={styles.notice} accessibilityRole="text">
          좌석은 결제 완료 시점까지 5분간 선점됩니다.
        </Text>

        <Pressable
          style={[styles.confirmButton, phase !== 'confirm' && styles.confirmButtonDisabled]}
          accessibilityRole="button"
          accessibilityLabel="결제하기"
          accessibilityState={{ disabled: phase !== 'confirm' }}
          disabled={phase !== 'confirm'}
          onPress={() => void handleConfirm()}
        >
          <Text style={styles.confirmButtonText}>결제하기</Text>
        </Pressable>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F2F2F7',
    paddingTop: 56,
  },
  backButton: {
    paddingHorizontal: 16,
    paddingBottom: 8,
  },
  backButtonText: {
    fontSize: 16,
    color: '#007AFF',
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyText: {
    fontSize: 15,
    color: '#8E8E93',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 14,
    color: '#8E8E93',
  },
  scrollContent: {
    paddingHorizontal: 16,
    paddingBottom: 40,
  },
  title: {
    fontSize: 22,
    fontWeight: 'bold',
    color: '#1C1C1E',
    marginBottom: 6,
  },
  eventName: {
    fontSize: 14,
    color: '#6C6C70',
    marginBottom: 20,
  },
  seatList: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    overflow: 'hidden',
    marginBottom: 16,
  },
  seatRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#E5E5EA',
  },
  seatLabel: {
    fontSize: 15,
    color: '#1C1C1E',
  },
  seatPrice: {
    fontSize: 15,
    fontWeight: '600',
    color: '#007AFF',
  },
  totalRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 16,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#C7C7CC',
    marginBottom: 12,
  },
  totalLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1C1C1E',
  },
  totalAmount: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#FF3B30',
  },
  notice: {
    fontSize: 12,
    color: '#8E8E93',
    marginBottom: 24,
    textAlign: 'center',
  },
  confirmButton: {
    backgroundColor: '#007AFF',
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  confirmButtonDisabled: {
    backgroundColor: '#C7C7CC',
  },
  confirmButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
  },
});
