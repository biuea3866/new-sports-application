/**
 * 내 주문 목록 화면
 */
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
} from 'react-native';
import { useState } from 'react';
import { useMyGoodsOrdersQuery } from '../../lib/useGoodsOrders';
import type { GoodsOrderResponse, GoodsOrderStatus } from '../../api/types';
import { useTheme } from '../../theme/useTheme';
import { createStyles } from '../../theme/createStyles';
import type { ThemeTokens } from '../../theme/tokens';

// BE `domain/goods/entity/GoodsOrderStatus.kt` 실측 재동기화 — 'PAID'·'PREPARING'은
// BE에 없다(결제완료는 'CONFIRMED').
const STATUS_LABEL: Record<GoodsOrderStatus, string> = {
  PENDING: '결제 대기',
  CONFIRMED: '결제 완료',
  SHIPPED: '배송 중',
  DELIVERED: '배송 완료',
  CANCELLED: '취소됨',
};

interface OrderCardProps {
  order: GoodsOrderResponse;
}

function OrderCard({ order }: OrderCardProps) {
  const { tokens } = useTheme();
  const styles = useStyles(tokens);
  const statusLabel = STATUS_LABEL[order.status];
  const firstItemName = order.items[0]?.productName ?? '상품';
  const extraCount = order.items.length - 1;
  const displayName = extraCount > 0 ? `${firstItemName} 외 ${extraCount}개` : firstItemName;

  return (
    <View
      style={styles.card}
      accessible={true}
      accessibilityLabel={`주문 번호 ${order.id}, ${displayName}, ${statusLabel}, ${Number(order.totalAmount).toLocaleString()}원`}
    >
      <View style={styles.cardHeader}>
        <Text style={styles.orderId} accessibilityRole="text">
          주문 #{order.id}
        </Text>
        <Text
          style={[
            styles.status,
            order.status === 'CANCELLED' && styles.statusCancelled,
            order.status === 'DELIVERED' && styles.statusDelivered,
          ]}
        >
          {statusLabel}
        </Text>
      </View>

      <Text style={styles.itemName} numberOfLines={2}>
        {displayName}
      </Text>

      <View style={styles.cardFooter}>
        <Text style={styles.date}>{new Date(order.createdAt).toLocaleDateString('ko-KR')}</Text>
        <Text style={styles.amount} accessibilityLabel={`합계 ${order.totalAmount}원`}>
          {Number(order.totalAmount).toLocaleString()}원
        </Text>
      </View>
    </View>
  );
}

const PAGE_SIZE = 20;

export default function OrderListScreen() {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useMyGoodsOrdersQuery(page, PAGE_SIZE);
  const { tokens } = useTheme();
  const styles = useStyles(tokens);

  if (isLoading) {
    return (
      <View style={styles.centered} accessibilityLabel="주문 목록 로딩 중">
        <ActivityIndicator size="large" color={tokens.accent} />
      </View>
    );
  }

  if (isError || data === undefined) {
    return (
      <View style={styles.centered} accessibilityLabel="주문 목록 오류">
        <Text style={styles.errorText} accessibilityRole="alert">
          주문 목록을 불러오지 못했습니다.
        </Text>
      </View>
    );
  }

  return (
    <View style={styles.container} accessible={false}>
      <FlatList
        data={data.content}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => <OrderCard order={item} />}
        ItemSeparatorComponent={() => <View style={styles.separator} />}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <View style={styles.centered}>
            <Text style={styles.emptyText} accessibilityRole="text">
              주문 내역이 없습니다.
            </Text>
          </View>
        }
      />

      {data.totalPages > 1 && (
        <View
          style={styles.pagination}
          accessibilityRole="toolbar"
          accessibilityLabel="페이지 이동"
        >
          <TouchableOpacity
            style={[styles.pageButton, page === 0 && styles.pageButtonDisabled]}
            onPress={() => setPage((prev) => prev - 1)}
            disabled={page === 0}
            accessibilityRole="button"
            accessibilityLabel="이전 페이지"
            accessibilityState={{ disabled: page === 0 }}
          >
            <Text style={[styles.pageButtonText, page === 0 && styles.pageButtonTextDisabled]}>
              이전
            </Text>
          </TouchableOpacity>

          <Text
            style={styles.pageInfo}
            accessibilityLabel={`${page + 1} / ${data.totalPages} 페이지`}
          >
            {page + 1} / {data.totalPages}
          </Text>

          <TouchableOpacity
            style={[styles.pageButton, page >= data.totalPages - 1 && styles.pageButtonDisabled]}
            onPress={() => setPage((prev) => prev + 1)}
            disabled={page >= data.totalPages - 1}
            accessibilityRole="button"
            accessibilityLabel="다음 페이지"
            accessibilityState={{ disabled: page >= data.totalPages - 1 }}
          >
            <Text
              style={[
                styles.pageButtonText,
                page >= data.totalPages - 1 && styles.pageButtonTextDisabled,
              ]}
            >
              다음
            </Text>
          </TouchableOpacity>
        </View>
      )}
    </View>
  );
}

const useStyles = createStyles((theme: ThemeTokens) =>
  StyleSheet.create({
    container: {
      flex: 1,
      backgroundColor: theme.background,
    },
    centered: {
      flex: 1,
      alignItems: 'center',
      justifyContent: 'center',
      paddingVertical: 40,
      backgroundColor: theme.background,
    },
    listContent: {
      padding: 12,
      gap: 0,
    },
    card: {
      backgroundColor: theme.surface,
      borderRadius: 12,
      padding: 14,
      marginBottom: 10,
    },
    cardHeader: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
      marginBottom: 8,
    },
    orderId: {
      fontSize: 13,
      color: theme.textMuted,
    },
    status: {
      fontSize: 13,
      color: theme.accent,
      fontWeight: '600',
    },
    statusCancelled: {
      color: theme.danger,
    },
    statusDelivered: {
      color: theme.success,
    },
    itemName: {
      fontSize: 15,
      color: theme.textPrimary,
      fontWeight: '500',
      marginBottom: 10,
    },
    cardFooter: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
    },
    date: {
      fontSize: 13,
      color: theme.textMuted,
    },
    amount: {
      fontSize: 16,
      color: theme.textPrimary,
      fontWeight: '700',
    },
    separator: {
      height: 0,
    },
    pagination: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'center',
      paddingVertical: 12,
      borderTopWidth: 1,
      borderTopColor: theme.border,
      gap: 20,
      backgroundColor: theme.surface,
    },
    pageButton: {
      paddingHorizontal: 20,
      paddingVertical: 8,
      borderRadius: 8,
      backgroundColor: theme.accent,
    },
    pageButtonDisabled: {
      backgroundColor: theme.border,
    },
    pageButtonText: {
      color: theme.accentText,
      fontWeight: '600',
      fontSize: 14,
    },
    pageButtonTextDisabled: {
      color: theme.textMuted,
    },
    pageInfo: {
      fontSize: 14,
      color: theme.textSecondary,
      minWidth: 60,
      textAlign: 'center',
    },
    errorText: {
      color: theme.danger,
      fontSize: 15,
    },
    emptyText: {
      color: theme.textMuted,
      fontSize: 15,
    },
  })
);
