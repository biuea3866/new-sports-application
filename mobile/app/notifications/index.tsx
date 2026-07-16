/**
 * 알림 목록 화면
 * GET /notifications/me — 알림 목록 (페이징)
 * GET /notifications/me/unread-count — 미읽음 카운트
 * PATCH /notifications/{id}/read — 읽음 처리
 */
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import {
  useNotifications,
  useUnreadCount,
  useMarkNotificationRead,
} from '../../lib/useNotifications';
import type { NotificationResponse, NotificationType } from '../../api/types';
import { useTheme } from '../../theme/useTheme';
import { createStyles } from '../../theme/createStyles';
import type { ThemeTokens } from '../../theme/tokens';

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
  const { tokens } = useTheme();
  const styles = useStyles(tokens);
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
  const { tokens } = useTheme();
  const styles = useStyles(tokens);

  const handleItemPress = (id: number) => {
    markRead(id);
  };

  return (
    <View style={styles.container} accessible={false}>
      <View style={styles.headerRow}>
        <Text style={styles.heading}>알림</Text>
        {unreadData && unreadData.unreadCount > 0 && (
          <View
            style={styles.unreadBadge}
            accessibilityLabel={`안읽은 알림 ${unreadData.unreadCount}개`}
          >
            <Text style={styles.unreadBadgeText}>{unreadData.unreadCount}</Text>
          </View>
        )}
      </View>

      {isLoading && (
        <View style={styles.center} accessibilityLabel="알림 목록 로딩 중">
          <ActivityIndicator size="large" color={tokens.accent} />
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
          renderItem={({ item }) => <NotificationItem item={item} onPress={handleItemPress} />}
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

const useStyles = createStyles((theme: ThemeTokens) =>
  StyleSheet.create({
    container: {
      flex: 1,
      backgroundColor: theme.background,
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
      color: theme.textPrimary,
    },
    unreadBadge: {
      backgroundColor: theme.danger,
      borderRadius: 10,
      minWidth: 20,
      height: 20,
      alignItems: 'center',
      justifyContent: 'center',
      paddingHorizontal: 5,
    },
    unreadBadgeText: {
      color: theme.accentText,
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
      color: theme.danger,
      marginBottom: 12,
    },
    retryButton: {
      paddingHorizontal: 20,
      paddingVertical: 10,
      backgroundColor: theme.accent,
      borderRadius: 8,
    },
    retryButtonText: {
      color: theme.accentText,
      fontSize: 14,
      fontWeight: '600',
    },
    list: {
      paddingHorizontal: 16,
      paddingBottom: 24,
    },
    emptyText: {
      textAlign: 'center',
      color: theme.textMuted,
      fontSize: 15,
      marginTop: 40,
    },
    item: {
      backgroundColor: theme.surface,
      borderRadius: 12,
      padding: 16,
      marginBottom: 8,
      shadowColor: theme.overlay,
      shadowOffset: { width: 0, height: 1 },
      shadowOpacity: 0.06,
      shadowRadius: 3,
      elevation: 1,
    },
    itemUnread: {
      borderLeftWidth: 3,
      borderLeftColor: theme.accent,
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
      backgroundColor: theme.border,
    },
    typeBadgeText: {
      fontSize: 11,
      fontWeight: '600',
      color: theme.textSecondary,
    },
    unreadDot: {
      width: 8,
      height: 8,
      borderRadius: 4,
      backgroundColor: theme.accent,
    },
    itemTitle: {
      fontSize: 15,
      fontWeight: '600',
      color: theme.textPrimary,
      marginBottom: 4,
    },
    itemContent: {
      fontSize: 13,
      color: theme.textSecondary,
      marginBottom: 6,
      lineHeight: 18,
    },
    itemDate: {
      fontSize: 12,
      color: theme.textMuted,
    },
  })
);
