/**
 * app/rooms/index.tsx — 채팅방 목록 화면 (S1, 재작성)
 *
 * 근거: FE-09 티켓, `20260704-채팅시스템고도화-design-fe-app.md` S1 텍스트 와이어프레임·
 * 상태 표. 토스 패턴: 리스트 아이템 + 우측 원형 안읽은 배지, 헤더 우측 단일 액션.
 *
 * 병합 로직(useRooms+useUnreadCounts)은 `lib/useRoomListItems`가 담당하고,
 * 이 화면은 4상태(loading/empty/error/success) 렌더링만 한다. 헤더 우측 초대 수신함
 * 진입 버튼은 `chat.community.enabled` 플래그로 게이팅한다(FE-15).
 */
import { FlatList, Pressable, RefreshControl, StyleSheet, View } from 'react-native';
import { router } from 'expo-router';
import { Badge } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorView } from '../../components/ui/ErrorView';
import { ListItem } from '../../components/ui/ListItem';
import { LoadingView } from '../../components/ui/LoadingView';
import { ThemedText } from '../../components/ui/ThemedText';
import { ThemedView } from '../../components/ui/ThemedView';
import { useTheme } from '../../theme/useTheme';
import { isFeatureEnabled } from '../../lib/feature-flags';
import { useMyInvitations } from '../../lib/useInvitations';
import { useRoomListItems, type RoomListItemView } from '../../lib/useRoomListItems';

const EMPTY_MESSAGE = '참여 중인 채팅방이 없어요';
const ERROR_MESSAGE = '채팅 목록을 불러오지 못했습니다.';

function InvitationInboxButton() {
  const { tokens } = useTheme();
  const { data: invitations } = useMyInvitations();
  const hasPendingInvitation = (invitations?.length ?? 0) > 0;
  const accessibilityLabel = hasPendingInvitation ? '초대함, 대기 중인 초대 있음' : '초대함';

  return (
    <Pressable
      style={styles.inboxButton}
      onPress={() => router.push('/invitations')}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
    >
      <ThemedText variant="accent" style={styles.inboxLabel}>
        초대함
      </ThemedText>
      {hasPendingInvitation ? (
        <View
          style={[styles.pendingDot, { backgroundColor: tokens.badge }]}
          accessibilityElementsHidden
        />
      ) : null}
    </Pressable>
  );
}

interface RoomListRowProps {
  item: RoomListItemView;
}

function RoomListRow({ item }: RoomListRowProps) {
  return (
    <ListItem
      title={item.displayName}
      subtitle={item.previewText ?? undefined}
      onPress={() => router.push(`/rooms/${item.id}`)}
      trailing={
        <View style={styles.trailing}>
          {item.timeLabel ? (
            <ThemedText variant="secondary" style={styles.timeLabel}>
              {item.timeLabel}
            </ThemedText>
          ) : null}
          <Badge count={item.unreadCount} />
        </View>
      }
    />
  );
}

export default function RoomsListScreen() {
  const { items, isLoading, isError, isRefreshing, refetch } = useRoomListItems();

  return (
    <ThemedView style={styles.container} testID="rooms-list-screen">
      <View style={styles.header}>
        <ThemedText variant="primary" style={styles.title}>
          채팅
        </ThemedText>
        {isFeatureEnabled('chat.community.enabled') ? <InvitationInboxButton /> : null}
      </View>

      {isLoading ? (
        <LoadingView variant="skeleton" />
      ) : isError ? (
        <ErrorView message={ERROR_MESSAGE} onRetry={refetch} />
      ) : items.length === 0 ? (
        <EmptyState message={EMPTY_MESSAGE} />
      ) : (
        <FlatList
          data={items}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => <RoomListRow item={item} />}
          contentContainerStyle={styles.list}
          ItemSeparatorComponent={() => <View style={styles.separator} />}
          refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={refetch} />}
        />
      )}
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: 60,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    marginBottom: 12,
  },
  title: {
    fontSize: 24,
    fontWeight: '700',
  },
  inboxButton: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  inboxLabel: {
    fontSize: 15,
    fontWeight: '600',
    marginRight: 6,
  },
  pendingDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  list: {
    paddingHorizontal: 16,
    paddingBottom: 40,
  },
  separator: {
    height: 8,
  },
  trailing: {
    alignItems: 'flex-end',
  },
  timeLabel: {
    fontSize: 12,
    marginBottom: 4,
  },
});
