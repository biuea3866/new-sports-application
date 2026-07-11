/**
 * LimitedDropPurchaseScreen — S2 구매 진행·결과 화면
 *
 * 근거: design-fe-app.md "S2 텍스트 와이어프레임" · "화면별 상태 표(loading/error/success)"
 * 티켓: FE-07-purchase-flow-screen.md
 *
 * 수량 선택 → 구매 확정 → usePurchaseLimitedDrop의 phase(admitted/tooEarly/soldOut/closed/
 * throttled/limit/error) 판별 결과를 그대로 렌더링한다(no-logic-in-component).
 * 429(THROTTLED) 자동 재시도는 usePurchaseLimitedDrop 훅이 이미 1회 수행하므로,
 * 화면에 도달한 throttled phase는 재시도 후에도 해소되지 않은 상태 — 수동 재시도 CTA만 제공한다.
 * admitted는 정상 성공, tooEarly/soldOut/closed/throttled/limit은 오류가 아닌 결과 상태이며
 * error(5xx·네트워크)만 진짜 오류로 표시한다.
 *
 * FE-08: bypassDenied(403 QUEUE_BYPASS_DENIED — 대기실 입장 토큰 없음/만료)는 "다시 대기하기"로
 * 대기실(`ROUTES.queue.waiting`)에 `router.replace` 재진입한다(design-fe-app.md "화면별 상태 표"
 * "error — 토큰 만료" · 시나리오 3).
 */
import { useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, TouchableOpacity } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { QuantityStepper } from '../../../components/limitedDrop/QuantityStepper';
import { PrimaryButton } from '../../../components/themed/PrimaryButton';
import { ThemedText } from '../../../components/themed/ThemedText';
import { ThemedView } from '../../../components/themed/ThemedView';
import { ROUTES } from '../../../lib/navigation';
import { useLimitedDrop } from '../../../lib/useLimitedDrop';
import { usePurchaseLimitedDrop } from '../../../lib/usePurchaseLimitedDrop';
import type { PurchaseLimitedDropPhase } from '../../../lib/usePurchaseLimitedDrop';

interface PurchaseResultViewProps {
  result: PurchaseLimitedDropPhase;
  perUserLimit: number;
  dropId: number;
  onRetry: () => void;
}

function BackToDetailLink({ dropId }: { dropId: number }) {
  const router = useRouter();
  return (
    <TouchableOpacity
      onPress={() => router.push(`/limited-drop/${dropId}`)}
      accessibilityRole="button"
      accessibilityLabel="상세로"
    >
      <ThemedText variant="accent">상세로</ThemedText>
    </TouchableOpacity>
  );
}

function PurchaseResultView({ result, perUserLimit, dropId, onRetry }: PurchaseResultViewProps) {
  const router = useRouter();

  if (result.phase === 'admitted') {
    return (
      <ThemedView style={styles.center} accessible accessibilityLabel="구매 성공">
        <ThemedText variant="success" style={styles.resultTitle}>
          구매가 완료됐어요
        </ThemedText>
        <ThemedText variant="secondary" style={styles.spacingBottom}>
          {`주문번호 ${result.data.orderId}`}
        </ThemedText>
        <PrimaryButton
          label="결제하기"
          onPress={() => router.push(`/payment/new?orderType=GOODS&orderId=${result.data.orderId}`)}
        />
      </ThemedView>
    );
  }

  if (result.phase === 'tooEarly') {
    return (
      <ThemedView style={styles.center} accessible accessibilityLabel="아직 판매 전이에요">
        <ThemedText variant="secondary" style={styles.spacingBottom}>
          {result.openAt ? `아직 판매 전이에요 · ${result.openAt}` : '아직 판매 전이에요'}
        </ThemedText>
        <BackToDetailLink dropId={dropId} />
      </ThemedView>
    );
  }

  if (result.phase === 'soldOut') {
    return (
      <ThemedView style={styles.center} accessible accessibilityLabel="아쉽게도 마감됐어요">
        <ThemedText variant="muted" style={styles.spacingBottom}>
          아쉽게도 마감됐어요
        </ThemedText>
        <BackToDetailLink dropId={dropId} />
      </ThemedView>
    );
  }

  if (result.phase === 'closed') {
    return (
      <ThemedView style={styles.center} accessible accessibilityLabel="판매가 종료됐어요">
        <ThemedText variant="muted" style={styles.spacingBottom}>
          판매가 종료됐어요
        </ThemedText>
        <BackToDetailLink dropId={dropId} />
      </ThemedView>
    );
  }

  if (result.phase === 'throttled') {
    return (
      <ThemedView style={styles.center} accessible accessibilityLabel="접속이 몰리고 있어요">
        <ThemedText variant="warning" style={styles.spacingBottom}>
          접속이 몰리고 있어요
        </ThemedText>
        <TouchableOpacity
          onPress={onRetry}
          accessibilityRole="button"
          accessibilityLabel="지금 재시도"
        >
          <ThemedText variant="accent">지금 재시도</ThemedText>
        </TouchableOpacity>
      </ThemedView>
    );
  }

  if (result.phase === 'limit') {
    return (
      <ThemedView style={styles.center} accessible accessibilityLabel="1인당 구매 한도 초과">
        <ThemedText variant="secondary" style={styles.spacingBottom}>
          {`1인당 ${perUserLimit}개까지 구매할 수 있어요`}
        </ThemedText>
        <BackToDetailLink dropId={dropId} />
      </ThemedView>
    );
  }

  if (result.phase === 'bypassDenied') {
    return (
      <ThemedView style={styles.center} accessible accessibilityLabel="대기 시간이 지났어요">
        <ThemedText variant="warning" style={styles.spacingBottom}>
          대기 시간이 지났어요
        </ThemedText>
        <TouchableOpacity
          onPress={() => router.replace(ROUTES.queue.waiting('limited-drop', String(dropId)))}
          accessibilityRole="button"
          accessibilityLabel="다시 대기하기"
        >
          <ThemedText variant="accent">다시 대기하기</ThemedText>
        </TouchableOpacity>
      </ThemedView>
    );
  }

  return (
    <ThemedView style={styles.center} accessible accessibilityLabel="일시 오류예요">
      <ThemedText variant="danger" style={styles.spacingBottom}>
        일시 오류예요
      </ThemedText>
      <TouchableOpacity onPress={onRetry} accessibilityRole="button" accessibilityLabel="다시 시도">
        <ThemedText variant="accent">다시 시도</ThemedText>
      </TouchableOpacity>
    </ThemedView>
  );
}

export default function LimitedDropPurchaseScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const dropId = Number(id);
  const { data: drop, isLoading: isDropLoading } = useLimitedDrop(dropId);
  const [quantity, setQuantity] = useState(1);
  const mutation = usePurchaseLimitedDrop(dropId);

  const handleSubmit = () => {
    mutation.mutate({ quantity });
  };

  if (isDropLoading || !drop) {
    return (
      <ThemedView style={styles.center} accessible accessibilityLabel="회차 정보 불러오는 중">
        <ActivityIndicator accessibilityElementsHidden />
      </ThemedView>
    );
  }

  if (mutation.isPending) {
    return (
      <ThemedView style={styles.center} accessible accessibilityLabel="구매 처리 중">
        <ActivityIndicator size="large" accessibilityElementsHidden />
        <ThemedText variant="secondary" style={styles.spacingTop}>
          구매 처리 중
        </ThemedText>
      </ThemedView>
    );
  }

  if (mutation.isSuccess && mutation.data) {
    return (
      <PurchaseResultView
        result={mutation.data}
        perUserLimit={drop.perUserLimit}
        dropId={dropId}
        onRetry={handleSubmit}
      />
    );
  }

  return (
    <ThemedView style={styles.container}>
      <ScrollView contentContainerStyle={styles.content}>
        <ThemedText variant="secondary" style={styles.spacingBottom}>
          구매 수량
        </ThemedText>
        <QuantityStepper value={quantity} max={drop.perUserLimit} onChange={setQuantity} />

        <PrimaryButton label="구매 확정" onPress={handleSubmit} />

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
    gap: 16,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  resultTitle: {
    fontSize: 20,
    fontWeight: '700',
    marginBottom: 8,
  },
  spacingTop: {
    marginTop: 12,
  },
  spacingBottom: {
    marginBottom: 12,
  },
  limitHint: {
    marginTop: 4,
    fontSize: 13,
  },
});
