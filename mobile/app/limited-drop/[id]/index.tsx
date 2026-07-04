/**
 * LimitedDropDetailScreen — S1 한정판 상세·카운트다운 화면
 *
 * 근거: design-fe-app.md "텍스트 와이어프레임 S1" · "화면별 상태 표"(loading/error/success)
 * 티켓: FE-06-detail-countdown-screen.md
 *
 * 데이터 조회·카운트다운 합성·상태별 CTA 결정은 useLimitedDropDetail 뷰모델 훅에 있다.
 * 이 컴포넌트는 뷰모델을 그대로 렌더링만 한다(no-logic-in-component).
 * 남은 수량 대비 총 한정 수량은 BE GET 응답 계약에 포함되지 않아(TDD "API 계약" 참고)
 * RemainingStockBar(비율 표시) 대신 remaining 값만 텍스트로 표시한다.
 */
import { ActivityIndicator, ScrollView, StyleSheet, TouchableOpacity } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { CountdownTimer } from '../../../components/limitedDrop/CountdownTimer';
import { DropStatusBadge } from '../../../components/limitedDrop/DropStatusBadge';
import { PrimaryButton } from '../../../components/themed/PrimaryButton';
import { ThemedText } from '../../../components/themed/ThemedText';
import { ThemedView } from '../../../components/themed/ThemedView';
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
            <ThemedText variant="secondary" accessibilityLabel={`남은 수량 ${drop.remaining}개`}>
              {`판매 중 · 남은 수량 ${drop.remaining}개`}
            </ThemedText>
          )}
          {drop.status === 'SOLD_OUT' && <ThemedText variant="muted">재고 소진</ThemedText>}
          {drop.status === 'CLOSED' && <ThemedText variant="muted">판매 종료</ThemedText>}
        </ThemedView>

        <PrimaryButton
          label={cta.label}
          disabled={cta.disabled}
          onPress={() => router.push(`/limited-drop/${drop.dropId}/purchase`)}
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
  limitHint: {
    marginTop: 12,
    fontSize: 13,
  },
});
