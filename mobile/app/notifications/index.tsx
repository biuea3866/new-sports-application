/**
 * 알림 목록 화면
 * GET /notifications/me — 알림 목록 (페이징)
 * GET /notifications/me/unread-count — 미읽음 카운트
 * PATCH /notifications/{id}/read — 읽음 처리
 */
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useNotifications, useUnreadCount, useMarkNotificationRead } from '../../lib/useNotifications';
import type { NotificationResponse, NotificationType } from '../../api/types';

function formatDate(iso: string): string {
  const date = new Date(iso);
  return date.toLocaleDateString('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function typeLabel(type: NotificationType): string {
  const map: Record<NotificationType, string> = {
    BOOKING: '예약',
    PAYMENT: '결제',
    EVENT: '이벤트',
    SYSTEM: '시스템',
    PROMOTION: '프로모션',
  };
  return map[type];
}

interface NotificationItemProps {
  item: NotificationResponse;
  onPress: (id: number) => void;
}

function NotificationItem({ item, onPress }: NotificationItemProps) {
  return (
    <Pressable
      style={[styles.item, !item.isRead && styles.itemUnread]}
      accessibilityRole="button"
      accessibilityLabel={`${item.title}, ${typeLabel(item.type)}, ${item.isRead ? '읽음' : '안읽음'}`}
      onPress={() => onPress(item.id)}
    >
      <View style={styles.itemHeader}>
        <View style={styles.typeBadge}>
          <Text style={styles.typeBadgeText}>{typeLabel(item.type)}</Text>
        </View>
        {!item.isRead && <View style={styles.unreadDot} accessibilityLabel="안읽음 표시" />}
      </View>
      <Text style={styles.itemTitle} numberOfLines={1}>
        {item.title}
      </Text>
      <Text style={styles.itemContent} numberOfLines={2}>
        {item.content}
      </Text>
      <Text style={styles.itemDate} accessibilityRole="text">
        {formatDate(item.createdAt)}
      </Text>
    </Pressable>
  );
}

export default function NotificationsScreen() {
  const { data, isLoading, isError, refetch } = useNotifications();
  const { data: unreadData } = useUnreadCount();
  const { mutate: markRead } = useMarkNotificationRead();

  const handleItemPress = (id: number) => {
    markRead(id);
  };

  return (
    <View style={styles.container} accessible={false}>
      <View style={styles.headerRow}>
        <Text style={styles.heading}>알림</Text>
        {unreadData && unreadData.unreadCount > 0 && (
          <View style={styles.unreadBadge} accessibilityLabel={`안읽은 알림 ${unreadData.unreadCount}개`}>
            <Text style={styles.unreadBadgeText}>{unreadData.unreadCount}</Text>
          </View>
        )}
      </View>

      {isLoading && (
        <View style={styles.center} accessibilityLabel="알림 목록 로딩 중">
          <ActivityIndicator size="large" color="#007AFF" />
        </View>
      )}

      {isError && !isLoading && (
        <View style={styles.center}>
          <Text style={styles.errorText} accessibilityRole="alert">
            알림을 불러올 수 없습니다.
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
          renderItem={({ item }) => (
            <NotificationItem item={item} onPress={handleItemPress} />
          )}
          contentContainerStyle={styles.list}
          ListEmptyComponent={
            <Text style={styles.emptyText} accessibilityRole="text">
              알림이 없습니다.
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
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    marginBottom: 12,
    gap: 8,
  },
  heading: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1C1C1E',
  },
  unreadBadge: {
    backgroundColor: '#FF3B30',
    borderRadius: 10,
    minWidth: 20,
    height: 20,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 5,
  },
  unreadBadgeText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '700',
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
  item: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    marginBottom: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.06,
    shadowRadius: 3,
    elevation: 1,
  },
  itemUnread: {
    borderLeftWidth: 3,
    borderLeftColor: '#007AFF',
  },
  itemHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 6,
  },
  typeBadge: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 6,
    backgroundColor: '#E5E5EA',
  },
  typeBadgeText: {
    fontSize: 11,
    fontWeight: '600',
    color: '#3C3C43',
  },
  unreadDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#007AFF',
  },
  itemTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1C1C1E',
    marginBottom: 4,
  },
  itemContent: {
    fontSize: 13,
    color: '#6C6C70',
    marginBottom: 6,
    lineHeight: 18,
  },
  itemDate: {
    fontSize: 12,
    color: '#8E8E93',
  },
});
