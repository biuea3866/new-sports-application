/**
 * 결제 상세 화면 — MO-08
 *
 * GET /payments/{id} 로 결제 상세를 조회합니다.
 * 표시 항목: 금액, 결제 수단, 상태, PG 거래 ID, 생성 일시, 완료 일시
 */
import { useEffect, useState } from 'react';
import { View, Text, StyleSheet, ActivityIndicator, ScrollView } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { getPayment, PaymentDetailResponse, PaymentStatus } from '../../api/payment';
import { useTheme } from '../../theme/useTheme';
import { createStyles } from '../../theme/createStyles';
import type { ThemeTokens } from '../../theme/tokens';

const STATUS_LABEL: Record<PaymentStatus, string> = {
  PENDING: '대기',
  READY: '준비',
  COMPLETED: '완료',
  CANCELLED: '취소',
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
  MOBILE_PAY: '모바일결제',
};

function formatDateTime(iso: string | null): string {
  if (!iso) return '-';
  const date = new Date(iso);
  return date.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

interface DetailRowProps {
  label: string;
  value: string;
}

function DetailRow({ label, value }: DetailRowProps) {
  const { tokens } = useTheme();
  const styles = useStyles(tokens);
  return (
    <View style={styles.row} accessible={true} accessibilityLabel={`${label}: ${value}`}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={styles.rowValue}>{value}</Text>
    </View>
  );
}

export default function PaymentDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { tokens } = useTheme();
  const styles = useStyles(tokens);

  const [detail, setDetail] = useState<PaymentDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const parsedId = parseInt(id, 10);
    if (isNaN(parsedId)) {
      setError('유효하지 않은 결제 ID입니다.');
      setLoading(false);
      return;
    }

    getPayment(parsedId)
      .then((data) => {
        setDetail(data);
      })
      .catch(() => {
        setError('결제 정보를 불러오지 못했습니다.');
      })
      .finally(() => {
        setLoading(false);
      });
  }, [id]);

  if (loading) {
    return (
      <View style={styles.centered} accessibilityLabel="결제 정보 불러오는 중">
        <ActivityIndicator size="large" color={tokens.accent} />
      </View>
    );
  }

  if (error || !detail) {
    return (
      <View style={styles.centered} accessibilityLabel="오류 화면">
        <Text style={styles.errorText}>{error ?? '결제 정보를 찾을 수 없습니다.'}</Text>
      </View>
    );
  }

  const isSuccess = detail.status === 'COMPLETED';

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title} accessibilityRole="header">
        결제 상세
      </Text>

      <View
        style={[styles.statusBadge, isSuccess ? styles.statusBadgeSuccess : styles.statusBadgeFail]}
        accessibilityLabel={`결제 상태: ${STATUS_LABEL[detail.status]}`}
      >
        <Text
          style={[styles.statusBadgeText, { color: isSuccess ? tokens.success : tokens.danger }]}
        >
          {STATUS_LABEL[detail.status]}
        </Text>
      </View>

      <View style={styles.card}>
        <DetailRow label="상품명" value={detail.itemName} />
        <DetailRow
          label="결제 금액"
          value={`${detail.amount.toLocaleString()}${detail.currency}`}
        />
        <DetailRow label="결제 수단" value={METHOD_LABEL[detail.method] ?? detail.method} />
        <DetailRow label="주문 번호" value={String(detail.id)} />
        <DetailRow label="PG 거래 ID" value={detail.pgTransactionId ?? '-'} />
        <DetailRow label="결제 요청" value={formatDateTime(detail.createdAt)} />
        <DetailRow label="결제 완료" value={formatDateTime(detail.paidAt)} />
      </View>
    </ScrollView>
  );
}

const useStyles = createStyles((theme: ThemeTokens) =>
  StyleSheet.create({
    container: {
      flexGrow: 1,
      padding: 24,
      backgroundColor: theme.background,
    },
    centered: {
      flex: 1,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: theme.background,
    },
    title: {
      fontSize: 20,
      fontWeight: '700',
      color: theme.textPrimary,
      marginBottom: 16,
    },
    statusBadge: {
      alignSelf: 'flex-start',
      paddingHorizontal: 12,
      paddingVertical: 6,
      borderRadius: 12,
      marginBottom: 24,
      backgroundColor: theme.surface,
    },
    statusBadgeSuccess: {
      backgroundColor: theme.surface,
    },
    statusBadgeFail: {
      backgroundColor: theme.surface,
    },
    statusBadgeText: {
      fontSize: 14,
      fontWeight: '600',
      color: theme.textPrimary,
    },
    card: {
      borderRadius: 12,
      borderWidth: 1,
      borderColor: theme.border,
      overflow: 'hidden',
    },
    row: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
      paddingHorizontal: 16,
      paddingVertical: 14,
      borderBottomWidth: 1,
      borderBottomColor: theme.border,
    },
    rowLabel: {
      fontSize: 14,
      color: theme.textMuted,
      flex: 1,
    },
    rowValue: {
      fontSize: 14,
      fontWeight: '500',
      color: theme.textPrimary,
      flex: 2,
      textAlign: 'right',
    },
    errorText: {
      fontSize: 14,
      color: theme.danger,
      textAlign: 'center',
    },
  })
);
