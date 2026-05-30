/**
 * 이벤트 상세 화면 — read-only
 * GET /events/{id} : 기본 정보 + 섹션별 좌석 수
 * 좌석 선택/발권은 후행 티켓에서 구현
 */
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { useEvent } from '../../../lib/useEvent';
import type { SectionAvailability } from '../../../api/types';

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
    <View style={styles.sectionRow} accessible={true} accessibilityLabel={`${section.section} 구역 총 ${section.totalSeats}석`}>
      <Text style={styles.sectionName}>{section.section}</Text>
      <Text style={styles.sectionSeats}>{section.totalSeats}석</Text>
    </View>
  );
}

export default function EventDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const eventId = Number(id);
  const { data, isLoading, isError, refetch } = useEvent(eventId);

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
});
