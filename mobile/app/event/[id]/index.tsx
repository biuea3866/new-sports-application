/**
 * 이벤트 상세 화면
 * GET /events/{id} : 기본 정보 + 섹션별 좌석 수 + 개별 좌석 목록
 * 좌석 선택 후 order 화면으로 진입
 *
 * 가상 대기열(FE-09, `20260709-가상대기열-design-fe-app.md` "라우팅·내비게이션 흐름"):
 * 예매 진입(티켓 구매 CTA)은 `virtual-queue.enabled` 플래그로 분기한다.
 * ON이면 대기실(`ticketing-event`, eventId 기준)을 경유하고, 대기실 뷰모델(`useWaitingRoom`,
 * FE-07)이 ADMITTED 시 `event order` 화면으로 전환한다(좌석은 대기실 통과 후 재확인).
 * OFF면 선택 좌석을 그대로 들고 기존처럼 order 화면으로 직접 이동한다.
 */
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useCallback, useMemo, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { useEvent } from '../../../lib/useEvent';
import { isFeatureEnabled } from '../../../lib/feature-flags';
import { ROUTES } from '../../../lib/navigation';
import type { SeatInfo, SectionAvailability } from '../../../api/types';

function formatDate(iso: string): string {
  const date = new Date(iso);
  return date.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    SCHEDULED: '예정',
    OPEN: '오픈',
    CLOSED: '종료',
  };
  return map[status] ?? status;
}

interface SectionRowProps {
  section: SectionAvailability;
}

function SectionRow({ section }: SectionRowProps) {
  return (
    <View
      style={styles.sectionRow}
      accessible={true}
      accessibilityLabel={`${section.section} 구역 총 ${section.totalSeats}석`}
    >
      <Text style={styles.sectionName}>{section.section}</Text>
      <Text style={styles.sectionSeats}>{section.totalSeats}석</Text>
    </View>
  );
}

interface SeatItemProps {
  seat: SeatInfo;
  selected: boolean;
  onToggle: (seatId: number) => void;
}

function SeatItem({ seat, selected, onToggle }: SeatItemProps) {
  const isUnavailable = !seat.available;
  return (
    <Pressable
      style={[
        styles.seatItem,
        selected && styles.seatItemSelected,
        isUnavailable && styles.seatItemUnavailable,
      ]}
      accessible={true}
      accessibilityRole="checkbox"
      accessibilityLabel={`${seat.section}구역 ${seat.rowNo}열 ${seat.seatNo}번${isUnavailable ? ' 선점중' : ''}`}
      accessibilityState={{ checked: selected, disabled: isUnavailable }}
      disabled={isUnavailable}
      onPress={() => onToggle(seat.id)}
    >
      <Text
        style={[
          styles.seatItemText,
          selected && styles.seatItemTextSelected,
          isUnavailable && styles.seatItemTextUnavailable,
        ]}
      >
        {isUnavailable ? '선점중' : `${seat.section}-${seat.rowNo}-${seat.seatNo}`}
      </Text>
    </Pressable>
  );
}

export default function EventDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const eventId = Number(id);
  const { data, isLoading, isError, refetch } = useEvent(eventId);
  const [selectedSeatIds, setSelectedSeatIds] = useState<number[]>([]);

  const toggleSeat = useCallback((seatId: number) => {
    setSelectedSeatIds((prev) =>
      prev.includes(seatId) ? prev.filter((s) => s !== seatId) : [...prev, seatId]
    );
  }, []);

  const hasSeats = useMemo(() => (data?.seats?.length ?? 0) > 0, [data]);

  const handleOrderPress = useCallback(() => {
    if (selectedSeatIds.length === 0) return;
    if (isFeatureEnabled('virtual-queue.enabled')) {
      router.push(ROUTES.queue.waiting('ticketing-event', String(eventId)));
      return;
    }
    router.push(`/event/${eventId}/order?seatIds=${selectedSeatIds.join(',')}`);
  }, [eventId, selectedSeatIds]);

  return (
    <View style={styles.container} accessible={false}>
      {/* 뒤로가기 */}
      <Pressable
        style={styles.backButton}
        accessibilityRole="button"
        accessibilityLabel="뒤로 가기"
        onPress={() => router.back()}
      >
        <Text style={styles.backButtonText}>{'< 뒤로'}</Text>
      </Pressable>

      {isLoading && (
        <View style={styles.center} accessibilityLabel="이벤트 정보 로딩 중">
          <ActivityIndicator size="large" color="#007AFF" />
        </View>
      )}

      {isError && !isLoading && (
        <View style={styles.center}>
          <Text style={styles.errorText} accessibilityRole="alert">
            이벤트 정보를 불러올 수 없습니다.
          </Text>
          <Pressable
            style={styles.retryButton}
            accessibilityRole="button"
            accessibilityLabel="다시 시도"
            onPress={() => void refetch()}
          >
            <Text style={styles.retryButtonText}>다시 시도</Text>
          </Pressable>
        </View>
      )}

      {!isLoading && !isError && data && (
        <ScrollView contentContainerStyle={styles.scrollContent}>
          {/* 상태 배지 */}
          <View
            style={[
              styles.statusBadge,
              data.status === 'OPEN' && styles.statusBadgeOpen,
              data.status === 'CLOSED' && styles.statusBadgeClosed,
            ]}
          >
            <Text style={styles.statusBadgeText}>{statusLabel(data.status)}</Text>
          </View>

          {/* 제목 */}
          <Text style={styles.title} accessibilityRole="header">
            {data.title}
          </Text>

          {/* 장소 / 일시 */}
          <View style={styles.infoSection}>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>장소</Text>
              <Text style={styles.infoValue} accessibilityRole="text">
                {data.venue}
              </Text>
            </View>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>일시</Text>
              <Text style={styles.infoValue} accessibilityRole="text">
                {formatDate(data.startsAt)}
              </Text>
            </View>
          </View>

          {/* 섹션별 좌석 정보 */}
          <Text style={styles.sectionHeading} accessibilityRole="header">
            구역별 좌석
          </Text>
          {data.sections.length === 0 ? (
            <Text style={styles.emptyText} accessibilityRole="text">
              좌석 정보가 없습니다.
            </Text>
          ) : (
            <View style={styles.sectionList}>
              {data.sections.map((section) => (
                <SectionRow key={section.section} section={section} />
              ))}
            </View>
          )}

          {/* 개별 좌석 선택 */}
          {hasSeats && data.status === 'OPEN' && (
            <>
              <Text style={styles.sectionHeading} accessibilityRole="header">
                좌석 선택
              </Text>
              <View style={styles.seatGrid} accessibilityLabel="좌석 목록">
                {data.seats.map((seat) => (
                  <SeatItem
                    key={seat.id}
                    seat={seat}
                    selected={selectedSeatIds.includes(seat.id)}
                    onToggle={toggleSeat}
                  />
                ))}
              </View>
              <Pressable
                style={[
                  styles.orderButton,
                  selectedSeatIds.length === 0 && styles.orderButtonDisabled,
                ]}
                accessibilityRole="button"
                accessibilityLabel={`티켓 구매 ${selectedSeatIds.length > 0 ? `${selectedSeatIds.length}석 선택됨` : ''}`}
                accessibilityState={{ disabled: selectedSeatIds.length === 0 }}
                disabled={selectedSeatIds.length === 0}
                onPress={handleOrderPress}
              >
                <Text style={styles.orderButtonText}>
                  {selectedSeatIds.length > 0
                    ? `티켓 구매 (${selectedSeatIds.length}석)`
                    : '좌석을 선택하세요'}
                </Text>
              </Pressable>
            </>
          )}
        </ScrollView>
      )}
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
    paddingVertical: 40,
  },
  errorText: {
    fontSize: 15,
    color: '#FF3B30',
    marginBottom: 12,
  },
  retryButton: {
    paddingHorizontal: 20,
    paddingVertical: 10,
    backgroundColor: '#007AFF',
    borderRadius: 8,
  },
  retryButtonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
  scrollContent: {
    paddingHorizontal: 16,
    paddingBottom: 40,
  },
  statusBadge: {
    alignSelf: 'flex-start',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
    backgroundColor: '#E5E5EA',
    marginBottom: 10,
  },
  statusBadgeOpen: {
    backgroundColor: '#34C759',
  },
  statusBadgeClosed: {
    backgroundColor: '#C7C7CC',
  },
  statusBadgeText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#FFFFFF',
  },
  title: {
    fontSize: 26,
    fontWeight: 'bold',
    color: '#1C1C1E',
    marginBottom: 20,
  },
  infoSection: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    marginBottom: 24,
    gap: 12,
  },
  infoRow: {
    flexDirection: 'row',
  },
  infoLabel: {
    width: 48,
    fontSize: 14,
    fontWeight: '600',
    color: '#6C6C70',
  },
  infoValue: {
    flex: 1,
    fontSize: 14,
    color: '#1C1C1E',
  },
  sectionHeading: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1C1C1E',
    marginBottom: 12,
  },
  sectionList: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    overflow: 'hidden',
  },
  sectionRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#E5E5EA',
  },
  sectionName: {
    fontSize: 15,
    color: '#1C1C1E',
  },
  sectionSeats: {
    fontSize: 15,
    fontWeight: '600',
    color: '#007AFF',
  },
  emptyText: {
    textAlign: 'center',
    color: '#8E8E93',
    fontSize: 15,
    marginTop: 16,
  },
  seatGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 20,
  },
  seatItem: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
    borderWidth: 1.5,
    borderColor: '#C7C7CC',
    backgroundColor: '#FFFFFF',
  },
  seatItemSelected: {
    borderColor: '#007AFF',
    backgroundColor: '#E5F0FF',
  },
  seatItemText: {
    fontSize: 13,
    color: '#1C1C1E',
    fontWeight: '500',
  },
  seatItemTextSelected: {
    color: '#007AFF',
    fontWeight: '700',
  },
  seatItemUnavailable: {
    borderColor: '#E5E5EA',
    backgroundColor: '#F2F2F7',
    opacity: 0.5,
  },
  seatItemTextUnavailable: {
    color: '#8E8E93',
  },
  orderButton: {
    backgroundColor: '#007AFF',
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
    marginTop: 4,
  },
  orderButtonDisabled: {
    backgroundColor: '#C7C7CC',
  },
  orderButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
  },
});
