/**
 * CancelApplicationSheet — 신청 취소 수수료 고지 바텀시트 (A-R6).
 *
 * 근거: design-fe-app.md "텍스트 와이어프레임" A-R6(토스 환불 확인 패턴). 환불 예정액은
 * `lib/recruitment-cancellation`(클라 미리보기)으로 표시하고, 실제 공제·환불액은
 * `POST /applications/{id}/cancel` 서버 확정값을 따른다(낙관적 업데이트 없음).
 */
import { useState } from 'react';
import { Modal, StyleSheet, View } from 'react-native';
import axios from 'axios';

import type { ApplicationResponse } from '../../api/recruitment';
import { useCancelApplication, useRecruitment } from '../../lib/useRecruitment';
import { calculateCancellationPreview } from '../../lib/recruitment-cancellation';
import { formatFeeAmount } from '../../lib/recruitment-format';
import { Button, LoadingView, ThemedText } from '../ui';
import { useTheme } from '../../theme/useTheme';

export interface CancelApplicationSheetProps {
  application: ApplicationResponse;
  onClose: () => void;
}

const CLOSED_MESSAGE = '마감되어 취소할 수 없어요';
const GENERIC_FAILURE_MESSAGE = '환불 처리 실패, 잠시 후 다시 시도해주세요';

export function CancelApplicationSheet({ application, onClose }: CancelApplicationSheetProps) {
  const { tokens } = useTheme();
  const { data: recruitment, isLoading } = useRecruitment(application.recruitmentId);
  const { mutate: cancelApplication, isPending } = useCancelApplication();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const preview =
    recruitment !== undefined
      ? calculateCancellationPreview(recruitment.feeAmount, recruitment.applicationDeadline)
      : null;

  const handleCancel = () => {
    setErrorMessage(null);
    cancelApplication(application.id, {
      onSuccess: onClose,
      onError: (error: unknown) => {
        const status = axios.isAxiosError(error) ? error.response?.status : undefined;
        setErrorMessage(status === 422 ? CLOSED_MESSAGE : GENERIC_FAILURE_MESSAGE);
      },
    });
  };

  const isBusy = isLoading || preview === null;
  const isCancelDisabled = isBusy || !preview.isCancellable || isPending;

  return (
    <Modal visible transparent animationType="slide" onRequestClose={onClose}>
      <View style={[styles.overlay, { backgroundColor: tokens.overlay }]}>
        <View style={[styles.sheet, { backgroundColor: tokens.surfaceElevated }]}>
          <ThemedText variant="primary" style={styles.title} accessibilityRole="header">
            신청을 취소할까요?
          </ThemedText>

          {isBusy ? (
            <LoadingView />
          ) : preview.tier === 'CLOSED' ? (
            <ThemedText variant="secondary" style={styles.body}>
              {CLOSED_MESSAGE}
            </ThemedText>
          ) : (
            <View>
              <ThemedText variant="primary" style={styles.refundAmount}>
                환불 예정 {formatFeeAmount(preview.refundAmount)}
              </ThemedText>
              {preview.tier === 'FREE' ? (
                <ThemedText variant="success" style={styles.feeText}>
                  전액 환불 · 수수료 없음
                </ThemedText>
              ) : (
                <ThemedText variant="warning" style={styles.feeText}>
                  수수료 {Math.round(preview.feeRate * 100)}% (
                  {formatFeeAmount(preview.deductedAmount)}) 공제
                </ThemedText>
              )}
              <ThemedText variant="secondary" style={styles.deadlineText}>
                마감까지 {preview.daysRemaining}일 남음
              </ThemedText>
              <ThemedText variant="danger" style={styles.warningText}>
                취소 후에는 되돌릴 수 없어요.
              </ThemedText>
            </View>
          )}

          {errorMessage ? (
            <ThemedText
              variant="danger"
              accessibilityRole="alert"
              accessibilityLabel={errorMessage}
              style={styles.errorText}
            >
              {errorMessage}
            </ThemedText>
          ) : null}

          <View style={styles.actions}>
            <View style={styles.actionButton}>
              <Button
                label="취소하기"
                onPress={handleCancel}
                disabled={isCancelDisabled}
                loading={isPending}
              />
            </View>
            <View style={styles.actionButton}>
              <Button label="닫기" onPress={onClose} variant="surface" />
            </View>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  sheet: {
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    paddingHorizontal: 20,
    paddingTop: 24,
    paddingBottom: 32,
  },
  title: {
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 16,
  },
  body: {
    fontSize: 15,
    marginBottom: 16,
  },
  refundAmount: {
    fontSize: 28,
    fontWeight: '800',
  },
  feeText: {
    fontSize: 13,
    marginTop: 6,
  },
  deadlineText: {
    fontSize: 13,
    marginTop: 4,
  },
  warningText: {
    fontSize: 13,
    marginTop: 16,
    fontWeight: '600',
  },
  errorText: {
    fontSize: 13,
    marginTop: 12,
  },
  actions: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 24,
  },
  actionButton: {
    flex: 1,
  },
});
