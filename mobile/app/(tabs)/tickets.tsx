/**
 * 티켓 탭 — 경기 목록 + 상태 필터
 * GET /events?page=0&size=20&status=...
 */
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { router } from 'expo-router';
import { useState } from 'react';
import { useEvents } from '../../lib/useEvents';
import { ROUTES } from '../../lib/navigation';
import type { EventResponse, EventStatus } from '../../api/types';

type FilterChip = { label: string; value: EventStatus | undefined };

const FILTER_CHIPS: FilterChip[] = [
  { label: '전체', value: undefined },
  { label: '예정', value: 'SCHEDULED' },
  { label: '오픈', value: 'OPEN' },
  { label: '종료', value: 'CLOSED' },
];

function statusLabel(status: EventStatus): string {
  const map: Record<EventStatus, string> = {
    SCHEDULED: '예정',
    OPEN: '오픈',
    CLOSED: '종료',
  };
  return map[status];
}

function formatDate(iso: string): string {
  const date = new Date(iso);
  return date.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

interface EventCardProps {
  event: EventResponse;
}

function EventCard({ event }: EventCardProps) {
  return (
    <Pressable
      style={styles.card}
      accessibilityRole="button"
      accessibilityLabel={`${event.title} 경기 상세 보기`}
      onPress={() => router.push(ROUTES.event.detail(String(event.id)))}
    >
      <View style={styles.cardHeader}>
        <Text style={styles.cardTitle} numberOfLines={1}>
          {event.title}
        </Text>
        <View
          style={[
            styles.statusBadge,
            event.status === 'OPEN' && styles.statusBadgeOpen,
            event.status === 'CLOSED' && styles.statusBadgeClosed,
          ]}
        >
          <Text style={styles.statusBadgeText}>{statusLabel(event.status)}</Text>
        </View>
      </View>
      <Text style={styles.cardVenue} accessibilityRole="text">
        {event.venue}
      </Text>
      <Text style={styles.cardDate} accessibilityRole="text">
        {formatDate(event.startsAt)}
      </Text>
    </Pressable>
  );
}

export default function TicketsScreen() {
  const [selectedStatus, setSelectedStatus] = useState<EventStatus | undefined>(
    undefined
  );

  const { data, isLoading, isError, refetch } = useEvents(0, 20, selectedStatus);

  return (
    <View style={styles.container} accessible={false}>
      <Text style={styles.heading}>경기 티켓</Text>

      {/* 상태 필터 칩 */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.chipRow}
        contentContainerStyle={styles.chipRowContent}
        accessibilityRole="tablist"
        accessibilityLabel="경기 상태 필터"
      >
        {FILTER_CHIPS.map((chip) => (
          <Pressable
            key={chip.label}
            style={[
              styles.chip,
              selectedStatus === chip.value && styles.chipActive,
            ]}
            accessibilityRole="tab"
            accessibilityLabel={`${chip.label} 필터`}
            accessibilityState={{ selected: selectedStatus === chip.value }}
            onPress={() => setSelectedStatus(chip.value)}
          >
            <Text
              style={[
                styles.chipText,
                selectedStatus === chip.value && styles.chipTextActive,
              ]}
            >
              {chip.label}
            </Text>
          </Pressable>
        ))}
      </ScrollView>

      {/* 목록 */}
      {isLoading && (
        <View style={styles.center} accessibilityLabel="경기 목록 로딩 중">
          <ActivityIndicator size="large" color="#007AFF" />
        </View>
      )}

      {isError && !isLoading && (
        <View style={styles.center}>
          <Text style={styles.errorText} accessibilityRole="alert">
            경기 목록을 불러올 수 없습니다.
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

      {!isLoading && !isError && (
        <FlatList
          data={data?.content ?? []}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => <EventCard event={item} />}
          contentContainerStyle={styles.list}
          ListEmptyComponent={
            <Text style={styles.emptyText} accessibilityRole="text">
              해당하는 경기가 없습니다.
            </Text>
          }
        />
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
  heading: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1C1C1E',
    paddingHorizontal: 16,
    marginBottom: 12,
  },
  chipRow: {
    flexGrow: 0,
    marginBottom: 12,
  },
  chipRowContent: {
    paddingHorizontal: 16,
    gap: 8,
  },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: '#E5E5EA',
  },
  chipActive: {
    backgroundColor: '#007AFF',
  },
  chipText: {
    fontSize: 14,
    color: '#3C3C43',
  },
  chipTextActive: {
    color: '#FFFFFF',
    fontWeight: '600',
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
  list: {
    paddingHorizontal: 16,
    paddingBottom: 24,
  },
  emptyText: {
    textAlign: 'center',
    color: '#8E8E93',
    fontSize: 15,
    marginTop: 40,
  },
  card: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.08,
    shadowRadius: 4,
    elevation: 2,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 6,
  },
  cardTitle: {
    flex: 1,
    fontSize: 16,
    fontWeight: '600',
    color: '#1C1C1E',
    marginRight: 8,
  },
  statusBadge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
    backgroundColor: '#E5E5EA',
  },
  statusBadgeOpen: {
    backgroundColor: '#34C759',
  },
  statusBadgeClosed: {
    backgroundColor: '#C7C7CC',
  },
  statusBadgeText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#FFFFFF',
  },
  cardVenue: {
    fontSize: 14,
    color: '#8E8E93',
    marginBottom: 4,
  },
  cardDate: {
    fontSize: 13,
    color: '#6C6C70',
  },
});
