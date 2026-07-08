/**
 * OrderHistoryItemCard — 내 주문 통합 조회(`/orders`) 목록 한 항목을 렌더하는 프레젠테이션
 * 카드. 근거: `design-fe-app.md` "컴포넌트 트리"·"와이어프레임 ②"·"테마 토큰 표", 티켓 `FE-07`.
 *
 * 주 표시명은 항상 `item.title`이다. title이 비어 있거나 누락된 경우에만
 * "유형명 #sourceId" fallback으로 대체한다(`formatOrderHistoryDisplayName`).
 * status/orderType 한글 라벨 매핑은 컴포넌트 내부에 분기 로직을 두지 않고
 * `lib/order-history-format`의 순수 유틸에 위임한다. 색은 항상 useTheme() 토큰만 경유한다.
 */
import { StyleSheet, View } from 'react-native';

import type { OrderHistoryItem } from '../../api/order-history-types';
import { Card, ThemedText } from '../ui';
import { useTheme } from '../../theme/useTheme';
import { formatRelativeTime } from '../../lib/post-format';
import {
  ORDER_TYPE_LABEL,
  formatOrderHistoryDisplayName,
  formatOrderHistoryStatusLabel,
  formatPaymentLabel,
  isPaymentConfirmedStatus,
} from '../../lib/order-history-format';

export interface OrderHistoryItemCardProps {
  item: OrderHistoryItem;
  onPress: (detailPath: string) => void;
}

export function OrderHistoryItemCard({ item, onPress }: OrderHistoryItemCardProps) {
  const { tokens } = useTheme();

  const orderTypeLabel = ORDER_TYPE_LABEL[item.orderType];
  const statusLabel = formatOrderHistoryStatusLabel(item.status);
  const displayName = formatOrderHistoryDisplayName(item);
  const paymentLabel = formatPaymentLabel(item.paymentId);
  const relativeTime = formatRelativeTime(item.createdAt);
  const showSuccessDot = isPaymentConfirmedStatus(item.status);

  const testId = `order-history-item-card-${item.orderType}-${item.sourceId}`;

  return (
    <Card
      testID={testId}
      onPress={() => onPress(item.detailPath)}
      accessibilityLabel={`${orderTypeLabel}, ${displayName}, ${statusLabel}`}
      style={styles.card}
    >
      <View style={styles.headerRow}>
        <View style={[styles.typeBadge, { backgroundColor: tokens.surfaceElevated }]}>
          <ThemedText variant="secondary" style={styles.typeBadgeLabel}>
            {orderTypeLabel}
          </ThemedText>
        </View>
        <View style={styles.statusGroup}>
          {showSuccessDot ? (
            <View
              testID={`${testId}-status-dot`}
              style={[styles.statusDot, { backgroundColor: tokens.success }]}
            />
          ) : null}
          <ThemedText variant="secondary" style={styles.statusLabel}>
            {statusLabel}
          </ThemedText>
        </View>
      </View>
      <ThemedText variant="primary" style={styles.title} numberOfLines={1}>
        {displayName}
      </ThemedText>
      <View style={styles.footerRow}>
        <ThemedText variant="secondary" style={styles.meta}>
          {paymentLabel}
        </ThemedText>
        <ThemedText variant="secondary" style={styles.meta}>
          {relativeTime}
        </ThemedText>
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: {
    marginBottom: 12,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  typeBadge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  typeBadgeLabel: {
    fontSize: 11,
    fontWeight: '700',
  },
  statusGroup: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  statusDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginRight: 6,
  },
  statusLabel: {
    fontSize: 12,
    fontWeight: '600',
  },
  title: {
    fontSize: 16,
    fontWeight: '600',
    marginTop: 10,
  },
  footerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 6,
  },
  meta: {
    fontSize: 12,
  },
});
