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

const STATUS_LABEL: Record<GoodsOrderStatus, string> = {
  PENDING: '결제 대기',
  PAID: '결제 완료',
  PREPARING: '준비 중',
  SHIPPED: '배송 중',
  DELIVERED: '배송 완료',
  CANCELLED: '취소됨',
};

interface OrderCardProps {
  order: GoodsOrderResponse;
}

function OrderCard({ order }: OrderCardProps) {
  const statusLabel = STATUS_LABEL[order.status];
  const firstItemName = order.items[0]?.productName ?? '상품';
  const extraCount = order.items.length - 1;
  const displayName =
    extraCount > 0 ? `${firstItemName} 외 ${extraCount}개` : firstItemName;

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
        <Text
          style={styles.amount}
          accessibilityLabel={`합계 ${order.totalAmount}원`}
        >
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

  if (isLoading) {
    return (
      <View style={styles.centered} accessibilityLabel="주문 목록 로딩 중">
        <ActivityIndicator size="large" color="#007AFF" />
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
            <Text
              style={[styles.pageButtonText, page === 0 && styles.pageButtonTextDisabled]}
            >
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
            style={[
              styles.pageButton,
              page >= data.totalPages - 1 && styles.pageButtonDisabled,
            ]}
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

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F2F2F7',
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 40,
  },
  listContent: {
    padding: 12,
    gap: 0,
  },
  card: {
    backgroundColor: '#fff',
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
    color: '#8E8E93',
  },
  status: {
    fontSize: 13,
    color: '#007AFF',
    fontWeight: '600',
  },
  statusCancelled: {
    color: '#FF3B30',
  },
  statusDelivered: {
    color: '#34C759',
  },
  itemName: {
    fontSize: 15,
    color: '#1C1C1E',
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
    color: '#8E8E93',
  },
  amount: {
    fontSize: 16,
    color: '#1C1C1E',
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
    borderTopColor: '#E5E5EA',
    gap: 20,
    backgroundColor: '#fff',
  },
  pageButton: {
    paddingHorizontal: 20,
    paddingVertical: 8,
    borderRadius: 8,
    backgroundColor: '#007AFF',
  },
  pageButtonDisabled: {
    backgroundColor: '#E5E5EA',
  },
  pageButtonText: {
    color: '#fff',
    fontWeight: '600',
    fontSize: 14,
  },
  pageButtonTextDisabled: {
    color: '#8E8E93',
  },
  pageInfo: {
    fontSize: 14,
    color: '#3C3C43',
    minWidth: 60,
    textAlign: 'center',
  },
  errorText: {
    color: '#FF3B30',
    fontSize: 15,
  },
  emptyText: {
    color: '#8E8E93',
    fontSize: 15,
  },
});
