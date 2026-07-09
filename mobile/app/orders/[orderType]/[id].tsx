/**
 * 주문 상세 화면(Option A+) — `/orders/[orderType]/[id]`
 *
 * 근거: 통합 주문내역(BE-08)이 `OrderHistoryItem.sourceId`/`detailPath`를 "주문 자신"의
 * 도메인 PK/경로로 확정(Option A)함에 따라, 통합 주문내역(`app/orders/index.tsx`) 항목
 * 탭 시 이 화면으로 이동한다(`lib/catalog-navigation.ts#resolveOrderRoute`). BE 4종
 * 주문상세 응답 보강(Option A+)이 origin/main에 머지되어 4개 도메인 모두 제목·주문 일시·
 * 원본 참조 PK를 제공한다.
 *
 * orderType(BOOKING/GOODS/TICKETING/RECRUITMENT) 4종을 단일 화면 + 분기로 처리한다.
 * 4개 도메인의 실제 GET 상세 응답은 필드 구성이 서로 달라(백엔드 소스 대조 결과 —
 * `lib/order-detail-format.ts` 주석 참조) `useOrderDetail`이 판별 유니온으로, 그 결과를
 * `lib/order-detail-format.ts`가 공통 `OrderDetailViewModel`로 정규화한다. 이 화면은
 * 배선·상태 분기만 담당한다(`no-logic-in-component`).
 *
 * 4상태: loading → 스켈레톤 / error(또는 지원하지 않는 orderType·id) → 안내 / success →
 * 제목·상태 배지·결제 정보·주문 일시·유형별 요약 + (참조 PK가 있을 때만) "원본 보기".
 */
import { ScrollView, StyleSheet, View } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';

import { useOrderDetail, type OrderDetailQueryResult } from '../../../lib/useOrderDetail';
import {
  toApplicationDetailViewModel,
  toBookingDetailViewModel,
  toGoodsOrderDetailViewModel,
  toTicketOrderDetailViewModel,
  type OrderDetailViewModel,
} from '../../../lib/order-detail-format';
import { ORDER_TYPE_LABEL } from '../../../lib/order-history-format';
import {
  Button,
  Card,
  EmptyState,
  ErrorView,
  LoadingView,
  ThemedText,
} from '../../../components/ui';
import { useTheme } from '../../../theme/useTheme';
import type { OrderType } from '../../../api/order-history-types';

const ERROR_MESSAGE = '주문 상세를 불러오지 못했어요';
const INVALID_MESSAGE = '지원하지 않는 주문 유형이에요';
const ORIGIN_LABEL = '원본 보기';
const PAYMENT_ROW_LABEL = '결제 정보';
const DATETIME_ROW_LABEL = '주문 일시';

function isOrderType(value: string | undefined): value is OrderType {
  return (
    value === 'BOOKING' || value === 'GOODS' || value === 'TICKETING' || value === 'RECRUITMENT'
  );
}

function resolveOrderType(value: string | undefined): OrderType {
  return isOrderType(value) ? value : 'BOOKING';
}

function toViewModel(id: number, result: OrderDetailQueryResult): OrderDetailViewModel {
  switch (result.orderType) {
    case 'BOOKING':
      return toBookingDetailViewModel(id, result.data);
    case 'GOODS':
      return toGoodsOrderDetailViewModel(id, result.data);
    case 'TICKETING':
      return toTicketOrderDetailViewModel(result.data);
    case 'RECRUITMENT':
      return toApplicationDetailViewModel(result.data);
  }
}

export default function OrderDetailScreen() {
  const { tokens } = useTheme();
  const { orderType: orderTypeParam, id: idParam } = useLocalSearchParams<{
    orderType: string;
    id: string;
  }>();

  const orderTypeValid = isOrderType(orderTypeParam);
  const orderType = resolveOrderType(orderTypeParam);
  const id = Number(idParam ?? NaN);
  const idValid = Number.isFinite(id) && id > 0;
  const isInputValid = orderTypeValid && idValid;

  const { data, isLoading, isError, refetch } = useOrderDetail(orderType, isInputValid ? id : 0);

  const viewModel = isInputValid && data ? toViewModel(id, data) : null;
  const originRoute = viewModel?.originRoute ?? null;

  const handleOriginPress = (route: string) => {
    router.push(route);
  };

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: tokens.background }]}
      contentContainerStyle={styles.content}
    >
      <ThemedText variant="primary" style={styles.header} accessibilityRole="header">
        주문 상세
      </ThemedText>

      {!isInputValid ? (
        <EmptyState message={INVALID_MESSAGE} />
      ) : isLoading ? (
        <LoadingView variant="skeleton" />
      ) : isError || viewModel === null ? (
        <ErrorView message={ERROR_MESSAGE} onRetry={() => void refetch()} />
      ) : (
        <Card style={styles.card}>
          <View style={styles.badgeRow}>
            <View style={[styles.typeBadge, { backgroundColor: tokens.surfaceElevated }]}>
              <ThemedText variant="secondary" style={styles.typeBadgeLabel}>
                {ORDER_TYPE_LABEL[orderType]}
              </ThemedText>
            </View>
            <View style={styles.statusGroup}>
              {viewModel.isPaymentConfirmed ? (
                <View
                  testID="order-detail-status-dot"
                  style={[styles.statusDot, { backgroundColor: tokens.success }]}
                />
              ) : null}
              <ThemedText variant="secondary" style={styles.statusLabel}>
                {viewModel.statusLabel}
              </ThemedText>
            </View>
          </View>

          <ThemedText variant="primary" style={styles.title}>
            {viewModel.title}
          </ThemedText>

          <View style={[styles.metaBox, { borderTopColor: tokens.border }]}>
            <View style={styles.metaRow}>
              <ThemedText variant="secondary" style={styles.metaLabel}>
                {PAYMENT_ROW_LABEL}
              </ThemedText>
              <ThemedText variant="primary" style={styles.metaValue}>
                {viewModel.paymentLabel}
              </ThemedText>
            </View>
            <View style={styles.metaRow}>
              <ThemedText variant="secondary" style={styles.metaLabel}>
                {DATETIME_ROW_LABEL}
              </ThemedText>
              <ThemedText variant="primary" style={styles.metaValue}>
                {viewModel.dateTimeLabel}
              </ThemedText>
            </View>
          </View>

          {viewModel.summaryLines.length > 0 ? (
            <View style={[styles.summaryBox, { borderTopColor: tokens.border }]}>
              {viewModel.summaryLines.map((line) => (
                <ThemedText key={line} variant="secondary" style={styles.summaryLine}>
                  {line}
                </ThemedText>
              ))}
            </View>
          ) : null}

          {originRoute !== null ? (
            <View style={styles.originAction}>
              <Button
                label={ORIGIN_LABEL}
                variant="surface"
                onPress={() => handleOriginPress(originRoute)}
              />
            </View>
          ) : null}
        </Card>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    paddingTop: 60,
    paddingHorizontal: 16,
    paddingBottom: 40,
  },
  header: {
    fontSize: 22,
    fontWeight: '700',
    marginBottom: 16,
  },
  card: {
    padding: 20,
  },
  badgeRow: {
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
    fontSize: 20,
    fontWeight: '700',
    marginTop: 14,
    marginBottom: 18,
  },
  metaBox: {
    borderTopWidth: 1,
    paddingTop: 14,
    gap: 10,
  },
  metaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  metaLabel: {
    fontSize: 14,
  },
  metaValue: {
    fontSize: 14,
    fontWeight: '600',
  },
  summaryBox: {
    borderTopWidth: 1,
    marginTop: 14,
    paddingTop: 14,
    gap: 6,
  },
  summaryLine: {
    fontSize: 13,
  },
  originAction: {
    marginTop: 20,
  },
});
