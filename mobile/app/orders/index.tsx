/**
 * 내 주문 통합 조회 화면 (`/orders`) — FE-10
 *
 * 근거: `20260708-상품주문-공유상위컨텍스트-design-fe-app.md` "텍스트 와이어프레임 ②"·
 * "화면별 상태 표 ②"·"컴포넌트 트리". 기존 `app/order/`(단수, goods 전용)와 구분해
 * `app/orders/`(복수)로 신설한다.
 *
 * `useOrderHistory(criteria)`(FE-05) + 지역 상태(orderType·status 필터)를 조립하는
 * 컨테이너. 데이터 가공(라벨 매핑·상세 경로 결정)은 `lib/order-history-format`·
 * `lib/catalog-navigation`에 위임하고, 이 화면은 배선·상태 분기만 담당한다
 * (`no-logic-in-component`).
 *
 * 4상태 + 미인증(401) 처리:
 * - loading → `LoadingView variant="skeleton"`
 * - error(401) → 로그인 유도("로그인이 필요해요" + "로그인하기" → `/(auth)/login`).
 *   be-client 인터셉터가 refresh 시도 후에도 실패하면 이미 로그인으로 리다이렉트하지만,
 *   화면은 방어적으로 동일한 유도 UI를 보여준다.
 * - error(그 외) → `ErrorView` + refetch
 * - success && 0건 → `EmptyState`
 * - success && failedDomains 있음 → `PartialFailureBanner` + 결과 정상 노출
 */
import { useState } from 'react';
import { FlatList, StyleSheet, View } from 'react-native';
import { router } from 'expo-router';

import { OrderHistoryItemCard } from '../../components/order/OrderHistoryItemCard';
import { PartialFailureBanner } from '../../components/common/PartialFailureBanner';
import { Button, EmptyState, ErrorView, LoadingView, SegmentedControl, ThemedText } from '../../components/ui';
import { useTheme } from '../../theme/useTheme';
import { useOrderHistory } from '../../lib/useOrderHistory';
import { isUnauthorizedError } from '../../lib/http-error';
import { resolveOrderRoute } from '../../lib/catalog-navigation';
import { ORDER_TYPE_LABEL } from '../../lib/order-history-format';
import type { OrderHistoryItem, OrderType } from '../../api/order-history-types';

const EMPTY_MESSAGE = '주문 내역이 없어요';
const EMPTY_DESCRIPTION = '예약·티켓·상품·모임 신청 내역이 여기에 모여요';
const ERROR_MESSAGE = '주문 내역을 불러오지 못했어요';
const LOGIN_REQUIRED_MESSAGE = '로그인이 필요해요';
const LOGIN_CTA_LABEL = '로그인하기';

type OrderTypeSegment = 'ALL' | OrderType;
type StatusSegment = 'ALL' | 'CONFIRMED' | 'PENDING' | 'CANCELLED';

const ORDER_TYPE_SEGMENT_OPTIONS: { label: string; value: OrderTypeSegment }[] = [
  { label: '전체', value: 'ALL' },
  { label: ORDER_TYPE_LABEL.BOOKING, value: 'BOOKING' },
  { label: ORDER_TYPE_LABEL.TICKETING, value: 'TICKETING' },
  { label: ORDER_TYPE_LABEL.GOODS, value: 'GOODS' },
  { label: ORDER_TYPE_LABEL.RECRUITMENT, value: 'RECRUITMENT' },
];

const STATUS_SEGMENT_OPTIONS: { label: string; value: StatusSegment }[] = [
  { label: '전체', value: 'ALL' },
  { label: '결제완료', value: 'CONFIRMED' },
  { label: '대기', value: 'PENDING' },
  { label: '취소', value: 'CANCELLED' },
];

function isOrderTypeSegment(value: string): value is OrderTypeSegment {
  return ORDER_TYPE_SEGMENT_OPTIONS.some((option) => option.value === value);
}

function isStatusSegment(value: string): value is StatusSegment {
  return STATUS_SEGMENT_OPTIONS.some((option) => option.value === value);
}

export default function OrderHistoryScreen() {
  const { tokens } = useTheme();
  const [orderType, setOrderType] = useState<OrderTypeSegment>('ALL');
  const [status, setStatus] = useState<StatusSegment>('ALL');

  const { data, isLoading, isError, error, refetch } = useOrderHistory({
    orderType: orderType === 'ALL' ? undefined : orderType,
    status: status === 'ALL' ? undefined : status,
  });

  const items = data?.items ?? [];
  const failedDomains = data?.failedDomains ?? [];
  const bannerLabels = failedDomains.map((domain) => ORDER_TYPE_LABEL[domain]);
  const unauthorized = isError && isUnauthorizedError(error);

  const handleOrderTypeChange = (value: string) => {
    if (isOrderTypeSegment(value)) {
      setOrderType(value);
    }
  };

  const handleStatusChange = (value: string) => {
    if (isStatusSegment(value)) {
      setStatus(value);
    }
  };

  const handleLoginPress = () => {
    router.replace('/(auth)/login');
  };

  const handleItemPress = (item: OrderHistoryItem) => {
    const detailRoute = resolveOrderRoute(item.orderType, item.sourceId);
    if (detailRoute === null) {
      return;
    }
    router.push(detailRoute);
  };

  return (
    <View style={[styles.container, { backgroundColor: tokens.background }]}>
      <ThemedText variant="primary" style={styles.title} accessibilityRole="header">
        내 주문 내역
      </ThemedText>

      <View style={styles.segmentWrapper}>
        <SegmentedControl
          options={ORDER_TYPE_SEGMENT_OPTIONS}
          value={orderType}
          onChange={handleOrderTypeChange}
        />
      </View>
      <View style={styles.segmentWrapper}>
        <SegmentedControl
          options={STATUS_SEGMENT_OPTIONS}
          value={status}
          onChange={handleStatusChange}
        />
      </View>

      <View style={styles.body}>
        {isLoading ? (
          <LoadingView variant="skeleton" />
        ) : unauthorized ? (
          <View style={styles.loginPrompt}>
            <ThemedText variant="secondary" style={styles.loginMessage}>
              {LOGIN_REQUIRED_MESSAGE}
            </ThemedText>
            <View style={styles.loginAction}>
              <Button label={LOGIN_CTA_LABEL} onPress={handleLoginPress} variant="accent" />
            </View>
          </View>
        ) : isError ? (
          <ErrorView message={ERROR_MESSAGE} onRetry={() => void refetch()} />
        ) : items.length === 0 ? (
          <EmptyState message={EMPTY_MESSAGE} description={EMPTY_DESCRIPTION} />
        ) : (
          <FlatList
            data={items}
            keyExtractor={(item) => `${item.orderType}-${item.sourceId}`}
            ListHeaderComponent={
              bannerLabels.length > 0 ? <PartialFailureBanner labels={bannerLabels} /> : null
            }
            renderItem={({ item }) => (
              <OrderHistoryItemCard item={item} onPress={() => handleItemPress(item)} />
            )}
            contentContainerStyle={styles.list}
          />
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: 60,
  },
  title: {
    fontSize: 24,
    fontWeight: '700',
    marginBottom: 16,
    paddingHorizontal: 16,
  },
  segmentWrapper: {
    marginBottom: 12,
    paddingHorizontal: 16,
  },
  body: {
    flex: 1,
  },
  list: {
    paddingHorizontal: 16,
    paddingBottom: 32,
  },
  loginPrompt: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 48,
    paddingHorizontal: 24,
  },
  loginMessage: {
    fontSize: 15,
    fontWeight: '600',
    textAlign: 'center',
    marginBottom: 16,
  },
  loginAction: {
    minWidth: 160,
  },
});
