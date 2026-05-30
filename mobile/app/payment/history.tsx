/**
 * 결제 내역 화면 — MO-12
 *
 * GET /payments/me?page=&size=&status=
 * 카드: 결제ID / amount / method / provider / status / paidAt
 * 상태 필터 (PENDING/COMPLETED/FAILED/REFUNDED)
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
import { usePaymentHistoryQuery } from '../../lib/usePaymentHistory';
import type { PaymentHistoryItem, PaymentHistoryStatus } from '../../api/types';
import type { PaymentStatus } from '../../api/payment';

const STATUS_LABEL: Record<PaymentHistoryStatus, string> = {
  PENDING: '결제 대기',
  COMPLETED: '결제 완료',
  FAILED: '실패',
  REFUNDED: '환불',
};

const METHOD_LABEL: Record<string, string> = {
  KAKAO: '카카오페이',
  TOSS: '토스페이',
  NAVER: '네이버페이',
  DANAL: '다날',
  CREDIT_CARD: '신용카드',
  BANK_TRANSFER: '계좌이체',
};

const FILTER_OPTIONS: Array<{ label: string; value: PaymentStatus | undefined }> = [
  { label: '전체', value: undefined },
  { label: '결제 완료', value: 'COMPLETED' },
  { label: '결제 대기', value: 'PENDING' },
  { label: '실패', value: 'FAILED' },
  { label: '환불', value: 'REFUNDED' },
];

const PAGE_SIZE = 20;

interface PaymentCardProps {
  item: PaymentHistoryItem;
}

function PaymentCard({ item }: PaymentCardProps) {
  const statusLabel = STATUS_LABEL[item.status];
  const methodLabel = METHOD_LABEL[item.method] ?? item.method;
  const displayDate = item.paidAt
    ? new Date(item.paidAt).toLocaleDateString('ko-KR')
    : new Date(item.createdAt).toLocaleDateString('ko-KR');

  return (
    <View
      style={styles.card}
      accessible={true}
      accessibilityLabel={`결제 ${item.id}, ${item.amount.toLocaleString()}원, ${methodLabel}, ${statusLabel}`}
    >
      <View style={styles.cardHeader}>
        <Text style={styles.paymentId} accessibilityRole="text">
          결제 #{item.id}
        </Text>
        <Text
          style={[
            styles.status,
            item.status === 'COMPLETED' && styles.statusCompleted,
            item.status === 'FAILED' && styles.statusFailed,
            item.status === 'REFUNDED' && styles.statusRefunded,
          ]}
        >
          {statusLabel}
        </Text>
      </View>

      <Text style={styles.amount} accessibilityLabel={`${item.amount.toLocaleString()}원`}>
        {item.amount.toLocaleString()}원
      </Text>

      <View style={styles.cardFooter}>
        <Text style={styles.method}>{methodLabel}</Text>
        {item.provider !== null && (
          <Text style={styles.provider}>{item.provider}</Text>
        )}
        <Text style={styles.date}>{displayDate}</Text>
      </View>
    </View>
  );
}

export default function PaymentHistoryScreen() {
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<PaymentStatus | undefined>(undefined);
  const { data, isLoading, isError } = usePaymentHistoryQuery(page, PAGE_SIZE, statusFilter);

  const handleFilterChange = (value: PaymentStatus | undefined) => {
    setStatusFilter(value);
    setPage(0);
  };

  if (isLoading) {
    return (
      <View style={styles.centered} accessibilityLabel="결제 내역 로딩 중">
        <ActivityIndicator size="large" color="#007AFF" />
      </View>
    );
  }

  if (isError || data === undefined) {
    return (
      <View style={styles.centered} accessibilityLabel="결제 내역 오류">
        <Text style={styles.errorText} accessibilityRole="alert">
          결제 내역을 불러오지 못했습니다.
        </Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View
        style={styles.filterBar}
        accessibilityRole="toolbar"
        accessibilityLabel="상태 필터"
      >
        {FILTER_OPTIONS.map(({ label, value }) => {
          const isActive = statusFilter === value;
          return (
            <TouchableOpacity
              key={label}
              style={[styles.filterChip, isActive && styles.filterChipActive]}
              onPress={() => handleFilterChange(value)}
              accessibilityRole="button"
              accessibilityLabel={`${label} 필터`}
              accessibilityState={{ selected: isActive }}
            >
              <Text style={[styles.filterChipText, isActive && styles.filterChipTextActive]}>
                {label}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>

      <FlatList
        data={data.content}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => <PaymentCard item={item} />}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <View style={styles.centered}>
            <Text style={styles.emptyText} accessibilityRole="text">
              결제 내역이 없습니다.
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
  filterBar: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5EA',
  },
  filterChip: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#C7C7CC',
    backgroundColor: '#fff',
  },
  filterChipActive: {
    borderColor: '#007AFF',
    backgroundColor: '#E3F2FD',
  },
  filterChipText: {
    fontSize: 13,
    color: '#3C3C43',
  },
  filterChipTextActive: {
    color: '#007AFF',
    fontWeight: '600',
  },
  listContent: {
    padding: 12,
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
  paymentId: {
    fontSize: 13,
    color: '#8E8E93',
  },
  status: {
    fontSize: 13,
    color: '#007AFF',
    fontWeight: '600',
  },
  statusCompleted: {
    color: '#34C759',
  },
  statusFailed: {
    color: '#FF3B30',
  },
  statusRefunded: {
    color: '#FF9500',
  },
  amount: {
    fontSize: 20,
    fontWeight: '700',
    color: '#1C1C1E',
    marginBottom: 10,
  },
  cardFooter: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  method: {
    fontSize: 13,
    color: '#3C3C43',
  },
  provider: {
    fontSize: 12,
    color: '#8E8E93',
  },
  date: {
    fontSize: 13,
    color: '#8E8E93',
    marginLeft: 'auto',
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
