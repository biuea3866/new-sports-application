/**
 * CommunityBookingSection — 모임 상세 소모임 예약 섹션(A-B1).
 *
 * 근거: design-fe-app.md "텍스트 와이어프레임" A-B2 인접, "화면별 4상태 표" A-B1(멤버 열람 /
 * 방장만 연결하기 노출 / 403 잠금 / 0건 empty). `GET /communities/{communityId}/bookings`는
 * `community.booking.enabled` 플래그 OFF 시 BE가 404를 반환한다(즉시 롤백 지점,
 * `api/communityBooking.ts` 주석) — 이 섹션은 컨테이너가 플래그로 렌더 자체를 막는다.
 */
import { FlatList, StyleSheet, View } from 'react-native';

import type { CommunityBookingListItemResponse } from '../../api/community-types';
import { useCommunityBookings } from '../../lib/useCommunityBooking';
import { isForbiddenError } from '../../lib/http-error';
import { Button, Card, EmptyState, ErrorView, LoadingView, ThemedText } from '../ui';

const LOCKED_MESSAGE = '🔒 멤버만 볼 수 있어요';
const EMPTY_MESSAGE = '연결된 예약이 없어요';
const ERROR_MESSAGE = '예약 목록을 불러오지 못했어요';
const UNKNOWN_INFO = '정보 없음';

export interface CommunityBookingSectionProps {
  communityId: number;
  /** 방장만 true — "연결하기" CTA 노출 여부. */
  canLink: boolean;
  onLinkPress: () => void;
}

interface CommunityBookingCardProps {
  booking: CommunityBookingListItemResponse;
}

function CommunityBookingCard({ booking }: CommunityBookingCardProps) {
  const metaLine = `${booking.facilityId ?? UNKNOWN_INFO} · ${booking.date ?? UNKNOWN_INFO} ${
    booking.timeRange ?? ''
  }`.trim();

  return (
    <Card style={styles.card} accessibilityLabel={`연결된 예약: ${metaLine}`}>
      <ThemedText variant="primary" style={styles.metaLine}>
        {metaLine}
      </ThemedText>
      <ThemedText variant="secondary" style={styles.capacity}>
        {booking.capacity !== null ? `정원 ${booking.capacity}명` : UNKNOWN_INFO}
      </ThemedText>
    </Card>
  );
}

export function CommunityBookingSection({
  communityId,
  canLink,
  onLinkPress,
}: CommunityBookingSectionProps) {
  const { data, isLoading, isError, error, refetch } = useCommunityBookings(communityId);
  const isLocked = isForbiddenError(error);
  const bookings = data ?? [];

  return (
    <View style={styles.container}>
      {canLink && (
        <View style={styles.ctaArea}>
          <Button label="예약 연결하기" onPress={onLinkPress} />
        </View>
      )}

      {isLoading && <LoadingView variant="skeleton" />}

      {!isLoading && isLocked && <EmptyState message={LOCKED_MESSAGE} />}

      {!isLoading && !isLocked && isError && (
        <ErrorView message={ERROR_MESSAGE} onRetry={() => void refetch()} />
      )}

      {!isLoading && !isLocked && !isError && bookings.length === 0 && (
        <EmptyState message={EMPTY_MESSAGE} />
      )}

      {!isLoading && !isLocked && !isError && bookings.length > 0 && (
        <FlatList
          data={bookings}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => <CommunityBookingCard booking={item} />}
          scrollEnabled={false}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginTop: 8,
  },
  ctaArea: {
    marginBottom: 16,
  },
  card: {
    marginBottom: 12,
  },
  metaLine: {
    fontSize: 15,
    fontWeight: '600',
  },
  capacity: {
    fontSize: 13,
    marginTop: 4,
  },
});
