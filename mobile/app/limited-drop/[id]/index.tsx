/**
 * LimitedDropDetailScreen — S1 한정판 상세·카운트다운 화면
 *
 * 근거: design-fe-app.md "텍스트 와이어프레임 S1" · "화면별 상태 표"(loading/error/success)
 * 티켓: FE-06-detail-countdown-screen.md
 *
 * 데이터 조회·카운트다운 합성·상태별 CTA 결정은 useLimitedDropDetail 뷰모델 훅에 있다.
 * 이 컴포넌트는 뷰모델을 그대로 렌더링만 한다(no-logic-in-component).
 * BE GET 응답에 totalQuantity·price가 결합되어(계약 확장) RemainingStockBar(비율 표시)와
 * 가격 표기를 노출한다.
 *
 * FE-08: 구매 CTA는 가상 대기열 플래그(`virtual-queue.enabled`)로 분기한다 — ON이면 대기실
 * (`ROUTES.queue.waiting`)로, OFF면 기존 구매 화면으로 직접 이동한다(design-fe-app.md "라우팅
 * 흐름" · 시나리오 6·7). 플래그 ON일 때 대기실을 우회할 경로를 두지 않는다.
 */
import { ActivityIndicator, ScrollView, StyleSheet, TouchableOpacity } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { CountdownTimer } from '../../../components/limitedDrop/CountdownTimer';
import { DropStatusBadge } from '../../../components/limitedDrop/DropStatusBadge';
import { RemainingStockBar } from '../../../components/limitedDrop/RemainingStockBar';
import { PrimaryButton } from '../../../components/themed/PrimaryButton';
import { ThemedText } from '../../../components/themed/ThemedText';
import { ThemedView } from '../../../components/themed/ThemedView';
import { isFeatureEnabled } from '../../../lib/feature-flags';
import { ROUTES } from '../../../lib/navigation';
import { useLimitedDropDetail } from '../../../lib/useLimitedDropDetail';

export default function LimitedDropDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const dropId = Number(id);
  const viewModel = useLimitedDropDetail(dropId);

  if (viewModel.phase === 'loading') {
    return (
      <ThemedView style={styles.center} accessible accessibilityLabel="회차 정보 불러오는 중">
        <ActivityIndicator accessibilityElementsHidden />
        <ThemedText variant="secondary" style={styles.spacingTop}>
          불러오는 중
        </ThemedText>
      </ThemedView>
    );
  }

  if (viewModel.phase === 'error') {
    return (
      <ThemedView style={styles.center} accessible accessibilityLabel={viewModel.message}>
        <ThemedText variant="secondary" style={styles.spacingBottom}>
          {viewModel.message}
        </ThemedText>
        <TouchableOpacity
          onPress={() => viewModel.retry()}
          accessibilityRole="button"
          accessibilityLabel="다시 시도"
        >
          <ThemedText variant="accent">다시 시도</ThemedText>
        </TouchableOpacity>
      </ThemedView>
    );
  }

  const { drop, remainingMs, cta } = viewModel;

  return (
    <ThemedView style={styles.container}>
      <ScrollView contentContainerStyle={styles.content}>
        <TouchableOpacity
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="뒤로 가기"
          style={styles.backButton}
        >
          <ThemedText variant="accent">{'< 뒤로'}</ThemedText>
        </TouchableOpacity>

        <DropStatusBadge status={drop.status} />

        <ThemedView style={styles.heroSection}>
          {drop.status === 'SCHEDULED' && (
            <>
              <ThemedText variant="secondary" style={styles.spacingBottom}>
                판매 시작까지
              </ThemedText>
              <CountdownTimer remainingMs={remainingMs} />
            </>
          )}
          {drop.status === 'OPEN' && (
            <RemainingStockBar remaining={drop.remaining} limited={drop.totalQuantity} />
          )}
          {drop.status === 'SOLD_OUT' && <ThemedText variant="muted">재고 소진</ThemedText>}
          {drop.status === 'CLOSED' && <ThemedText variant="muted">판매 종료</ThemedText>}
          <ThemedText
            variant="primary"
            style={styles.priceText}
            accessibilityLabel={`가격 ${drop.price.toLocaleString()}원`}
          >
            {`${drop.price.toLocaleString()}원`}
          </ThemedText>
        </ThemedView>

        <PrimaryButton
          label={cta.label}
          disabled={cta.disabled}
          onPress={() =>
            router.push(
              isFeatureEnabled('virtual-queue.enabled')
                ? ROUTES.queue.waiting('limited-drop', String(drop.dropId))
                : ROUTES.limitedDrop.purchase(String(drop.dropId))
            )
          }
        />

        <ThemedText variant="muted" style={styles.limitHint}>
          {`1인당 최대 ${drop.perUserLimit}개 구매 가능`}
        </ThemedText>
      </ScrollView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    padding: 20,
    paddingBottom: 40,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  backButton: {
    paddingVertical: 12,
  },
  heroSection: {
    paddingVertical: 24,
    alignItems: 'flex-start',
  },
  spacingTop: {
    marginTop: 12,
  },
  spacingBottom: {
    marginBottom: 12,
  },
  priceText: {
    marginTop: 12,
    fontSize: 20,
    fontWeight: '700',
  },
  limitHint: {
    marginTop: 12,
    fontSize: 13,
  },
});
