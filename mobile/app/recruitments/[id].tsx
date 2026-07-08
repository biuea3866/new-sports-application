/**
 * 모집 상세 화면 — A-R2
 *
 * 근거: design-fe-app.md "텍스트 와이어프레임" A-R2(토스 결제 상세 패턴 — 상단 요약 카드 +
 * 하단 고정 단일 CTA), "결제 흐름 재사용 결정"(신청+결제 개시 → pre-issued 결제 화면 진입).
 *
 * 결제수단은 이 화면에서 별도로 선택하지 않는다 — 와이어프레임에 단일 CTA만 정의돼 있어
 * 신청 시 기본 결제수단(CREDIT_CARD)으로 즉시 신청을 개시하고, 서버가 prepare한
 * checkoutUrl로 결제 화면(pre-issued 모드)에 진입한다.
 */
import { useState } from 'react';
import { Alert, ScrollView, StyleSheet, View } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import axios from 'axios';

import { useCurrentUserId } from '../../api/goods';
import {
  useApplyRecruitment,
  useCancelRecruitment,
  useRecruitment,
} from '../../lib/useRecruitment';
import { formatDeadlineDday, formatFeeAmount } from '../../lib/recruitment-format';
import { Button, Card, EmptyState, ErrorView, LoadingView, ThemedText } from '../../components/ui';
import { useTheme } from '../../theme/useTheme';

const NOT_FOUND_MESSAGE = '삭제되었거나 없는 모집이에요';
const LOCK_MESSAGE = '이 모집은 모임 멤버만 볼 수 있어요';
const GENERIC_ERROR_MESSAGE = '모집 정보를 불러오지 못했어요';
const FULL_MESSAGE = '마감됨';
const APPLY_GENERIC_ERROR_MESSAGE = '신청에 실패했어요. 다시 시도해주세요';

function getErrorStatus(error: unknown): number | undefined {
  return axios.isAxiosError(error) ? error.response?.status : undefined;
}

export default function RecruitmentDetailScreen() {
  const { tokens } = useTheme();
  const { id } = useLocalSearchParams<{ id: string }>();
  const recruitmentId = Number(id ?? NaN);
  const currentUserId = useCurrentUserId();

  const [applyError, setApplyError] = useState<string | null>(null);

  const { data: recruitment, isLoading, isError, error, refetch } = useRecruitment(recruitmentId);
  const { mutate: applyRecruitment, isPending: isApplying } = useApplyRecruitment(recruitmentId);
  const { mutate: cancelRecruitment } = useCancelRecruitment();

  const errorStatus = getErrorStatus(error);

  function handleApply() {
    if (recruitment === undefined) {
      return;
    }
    setApplyError(null);
    applyRecruitment(
      { paymentMethod: 'CREDIT_CARD', currency: 'KRW' },
      {
        onSuccess: (result) => {
          if (result.checkoutUrl !== null && result.paymentId !== null) {
            router.push(
              `/payment/new?orderType=RECRUITMENT&orderId=${result.id}&paymentId=${result.paymentId}&checkoutUrl=${encodeURIComponent(
                result.checkoutUrl
              )}`
            );
            return;
          }
          router.push('/recruitments/me');
        },
        onError: (applyErr) => {
          setApplyError(
            getErrorStatus(applyErr) === 409 ? FULL_MESSAGE : APPLY_GENERIC_ERROR_MESSAGE
          );
        },
      }
    );
  }

  function handleCancelRecruitment() {
    Alert.alert('모집 취소', '모집을 취소할까요? 신청자에게 환불이 진행돼요.', [
      { text: '닫기', style: 'cancel' },
      {
        text: '취소하기',
        style: 'destructive',
        onPress: () => cancelRecruitment(recruitmentId),
      },
    ]);
  }

  if (isLoading) {
    return (
      <View style={[styles.container, { backgroundColor: tokens.background }]}>
        <LoadingView />
      </View>
    );
  }

  if (isError || recruitment === undefined) {
    return (
      <View style={[styles.container, { backgroundColor: tokens.background }]}>
        {errorStatus === 404 ? (
          <EmptyState message={NOT_FOUND_MESSAGE} />
        ) : errorStatus === 403 ? (
          <EmptyState message={LOCK_MESSAGE} />
        ) : (
          <ErrorView message={GENERIC_ERROR_MESSAGE} onRetry={() => void refetch()} />
        )}
      </View>
    );
  }

  const isRecruiter = currentUserId === recruitment.recruiterUserId;
  const isApplyDisabled = recruitment.status !== 'OPEN' || isApplying;
  const applyLabel =
    recruitment.status !== 'OPEN'
      ? '모집 마감됨'
      : `신청하기 · ${formatFeeAmount(recruitment.feeAmount)}`;

  return (
    <View style={[styles.container, { backgroundColor: tokens.background }]}>
      <ScrollView contentContainerStyle={styles.content}>
        <ThemedText variant="primary" style={styles.title} accessibilityRole="header">
          {recruitment.title}
        </ThemedText>

        <Card style={styles.summaryCard}>
          <View style={styles.summaryRow}>
            <ThemedText variant="secondary" style={styles.summaryLabel}>
              참가비
            </ThemedText>
            <ThemedText variant="primary" style={styles.summaryValue}>
              {formatFeeAmount(recruitment.feeAmount)}
            </ThemedText>
          </View>
          <View style={styles.summaryRow}>
            <ThemedText variant="secondary" style={styles.summaryLabel}>
              정원
            </ThemedText>
            <ThemedText variant="primary" style={styles.summaryValue}>
              {recruitment.capacity}명
            </ThemedText>
          </View>
          <View style={styles.summaryRow}>
            <ThemedText variant="secondary" style={styles.summaryLabel}>
              활동일
            </ThemedText>
            <ThemedText variant="primary" style={styles.summaryValue}>
              {new Date(recruitment.activityAt).toLocaleString('ko-KR')}
            </ThemedText>
          </View>
          <View style={styles.summaryRow}>
            <ThemedText variant="secondary" style={styles.summaryLabel}>
              마감
            </ThemedText>
            <ThemedText variant="primary" style={styles.summaryValue}>
              {new Date(recruitment.applicationDeadline).toLocaleString('ko-KR')} (
              {formatDeadlineDday(recruitment.applicationDeadline)})
            </ThemedText>
          </View>
        </Card>

        {recruitment.description !== null && recruitment.description.length > 0 ? (
          <ThemedText variant="secondary" style={styles.description}>
            {recruitment.description}
          </ThemedText>
        ) : null}

        {isRecruiter ? (
          <View style={styles.recruiterActions}>
            <Button
              label="신청자 보기"
              variant="surface"
              onPress={() => router.push(`/recruitments/${recruitment.id}/applications`)}
            />
            <View style={styles.recruiterActionSpacer} />
            <Button label="모집 취소" variant="surface" onPress={handleCancelRecruitment} />
          </View>
        ) : null}

        {applyError ? (
          <ThemedText
            variant="danger"
            accessibilityRole="alert"
            accessibilityLabel={applyError}
            style={styles.applyError}
          >
            {applyError}
          </ThemedText>
        ) : null}
      </ScrollView>

      {!isRecruiter ? (
        <View style={styles.ctaWrapper}>
          <Button
            label={applyLabel}
            onPress={handleApply}
            disabled={isApplyDisabled}
            loading={isApplying}
          />
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: 60,
  },
  content: {
    paddingHorizontal: 16,
    paddingBottom: 24,
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    marginBottom: 16,
  },
  summaryCard: {
    marginBottom: 16,
  },
  summaryRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 6,
  },
  summaryLabel: {
    fontSize: 13,
  },
  summaryValue: {
    fontSize: 14,
    fontWeight: '600',
  },
  description: {
    fontSize: 14,
    lineHeight: 20,
    marginBottom: 16,
  },
  recruiterActions: {
    flexDirection: 'row',
    marginTop: 8,
  },
  recruiterActionSpacer: {
    width: 12,
  },
  applyError: {
    fontSize: 13,
    marginTop: 16,
  },
  ctaWrapper: {
    paddingHorizontal: 16,
    paddingBottom: 24,
    paddingTop: 12,
  },
});
