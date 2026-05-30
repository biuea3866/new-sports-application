/**
 * 결제 화면 — MO-08
 *
 * Query string: orderType / orderId / amount / method
 * 결제수단 선택 → "결제하기" → POST /payments (Idempotency-Key 자동 생성)
 * 결과: COMPLETED → 성공 화면, FAILED → 실패 화면
 */
import { useState, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { createPayment, PaymentMethod, OrderType, PaymentStatus } from '../../api/payment';

type PaymentPhase = 'select' | 'loading' | 'done';

interface DoneState {
  status: PaymentStatus;
  paymentId: number;
}

interface MethodOption {
  method: PaymentMethod;
  label: string;
}

const METHOD_OPTIONS: MethodOption[] = [
  { method: 'KAKAO', label: '카카오페이' },
  { method: 'TOSS', label: '토스페이' },
  { method: 'NAVER', label: '네이버페이' },
  { method: 'DANAL', label: '다날' },
  { method: 'CREDIT_CARD', label: '신용카드' },
  { method: 'BANK_TRANSFER', label: '계좌이체' },
];

function generateUUID(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = (Math.random() * 16) | 0;
    const value = char === 'x' ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}

function isValidOrderType(value: string | string[] | undefined): value is OrderType {
  return (
    typeof value === 'string' && ['BOOKING', 'TICKETING', 'GOODS'].includes(value)
  );
}

interface SelectPhaseProps {
  amount: number;
  selectedMethod: PaymentMethod | null;
  onSelectMethod: (method: PaymentMethod) => void;
  onPay: () => void;
}

function SelectPhase({ amount, selectedMethod, onSelectMethod, onPay }: SelectPhaseProps) {
  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title} accessibilityRole="header">
        결제 수단 선택
      </Text>
      <Text style={styles.amountText} accessibilityLabel={`결제 금액 ${amount.toLocaleString()}원`}>
        {amount.toLocaleString()}원
      </Text>

      <View style={styles.methodGrid} accessibilityLabel="결제 수단 목록">
        {METHOD_OPTIONS.map(({ method, label }) => {
          const isSelected = selectedMethod === method;
          return (
            <TouchableOpacity
              key={method}
              style={[styles.methodButton, isSelected && styles.methodButtonSelected]}
              onPress={() => onSelectMethod(method)}
              accessibilityRole="radio"
              accessibilityLabel={label}
              accessibilityState={{ selected: isSelected }}
            >
              <Text style={[styles.methodLabel, isSelected && styles.methodLabelSelected]}>
                {label}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>

      <TouchableOpacity
        style={[styles.payButton, !selectedMethod && styles.payButtonDisabled]}
        onPress={onPay}
        disabled={!selectedMethod}
        accessibilityRole="button"
        accessibilityLabel="결제하기"
        accessibilityState={{ disabled: !selectedMethod }}
      >
        <Text style={styles.payButtonLabel}>결제하기</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

interface DonePhaseProps {
  doneState: DoneState;
  onRetry: () => void;
  onGoBack: () => void;
}

function DonePhase({ doneState, onRetry, onGoBack }: DonePhaseProps) {
  const isSuccess = doneState.status === 'COMPLETED';
  return (
    <View style={styles.container}>
      <Text
        style={[styles.resultIcon]}
        accessibilityLabel={isSuccess ? '결제 성공' : '결제 실패'}
      >
        {isSuccess ? '✓' : '✗'}
      </Text>
      <Text style={styles.resultTitle}>{isSuccess ? '결제 완료' : '결제 실패'}</Text>
      <Text style={styles.resultDesc}>
        {isSuccess
          ? `결제가 완료되었습니다. (주문번호 ${doneState.paymentId})`
          : '결제에 실패했습니다. 다시 시도해주세요.'}
      </Text>

      {isSuccess ? (
        <TouchableOpacity
          style={styles.payButton}
          onPress={onGoBack}
          accessibilityRole="button"
          accessibilityLabel="확인"
        >
          <Text style={styles.payButtonLabel}>확인</Text>
        </TouchableOpacity>
      ) : (
        <TouchableOpacity
          style={styles.payButton}
          onPress={onRetry}
          accessibilityRole="button"
          accessibilityLabel="다시 시도"
        >
          <Text style={styles.payButtonLabel}>다시 시도</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

export default function PaymentScreen() {
  const params = useLocalSearchParams();
  const router = useRouter();

  const { orderType, orderId, amount: amountParam, method: methodParam } = params;

  const parsedAmount = typeof amountParam === 'string' ? parseInt(amountParam, 10) : NaN;
  const initialMethod =
    typeof methodParam === 'string' &&
    METHOD_OPTIONS.some((o) => o.method === methodParam)
      ? (methodParam as PaymentMethod)
      : null;

  const [phase, setPhase] = useState<PaymentPhase>('select');
  const [selectedMethod, setSelectedMethod] = useState<PaymentMethod | null>(initialMethod);
  const [doneState, setDoneState] = useState<DoneState | null>(null);

  const handlePay = useCallback(async () => {
    if (!selectedMethod) return;

    if (!isValidOrderType(orderType)) {
      Alert.alert('오류', '유효하지 않은 주문 유형입니다.');
      return;
    }

    if (isNaN(parsedAmount) || parsedAmount <= 0) {
      Alert.alert('오류', '유효하지 않은 결제 금액입니다.');
      return;
    }

    const parsedOrderId = typeof orderId === 'string' ? parseInt(orderId, 10) : NaN;
    if (isNaN(parsedOrderId)) {
      Alert.alert('오류', '유효하지 않은 주문 ID입니다.');
      return;
    }

    setPhase('loading');

    try {
      const idempotencyKey = generateUUID();
      const result = await createPayment(
        {
          orderType,
          orderId: parsedOrderId,
          method: selectedMethod,
          amount: parsedAmount,
          currency: 'KRW',
        },
        idempotencyKey
      );
      setDoneState({ status: result.status, paymentId: result.id });
      setPhase('done');
    } catch {
      setDoneState({ status: 'FAILED', paymentId: 0 });
      setPhase('done');
    }
  }, [selectedMethod, orderType, orderId, parsedAmount]);

  const handleRetry = useCallback(() => {
    setPhase('select');
    setDoneState(null);
  }, []);

  const handleGoBack = useCallback(() => {
    router.back();
  }, [router]);

  if (phase === 'loading') {
    return (
      <View style={styles.centered} accessibilityLabel="결제 처리 중">
        <ActivityIndicator size="large" color="#1976D2" />
        <Text style={styles.loadingText}>결제 처리 중...</Text>
      </View>
    );
  }

  if (phase === 'done' && doneState) {
    return <DonePhase doneState={doneState} onRetry={handleRetry} onGoBack={handleGoBack} />;
  }

  return (
    <SelectPhase
      amount={isNaN(parsedAmount) ? 0 : parsedAmount}
      selectedMethod={selectedMethod}
      onSelectMethod={setSelectedMethod}
      onPay={() => void handlePay()}
    />
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    padding: 24,
    backgroundColor: '#FFFFFF',
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FFFFFF',
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    color: '#1C1C1E',
    marginBottom: 8,
  },
  amountText: {
    fontSize: 32,
    fontWeight: '800',
    color: '#1976D2',
    marginBottom: 32,
  },
  methodGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
    marginBottom: 40,
  },
  methodButton: {
    width: '47%',
    paddingVertical: 16,
    borderRadius: 8,
    borderWidth: 1.5,
    borderColor: '#E0E0E0',
    alignItems: 'center',
    backgroundColor: '#FAFAFA',
  },
  methodButtonSelected: {
    borderColor: '#1976D2',
    backgroundColor: '#E3F2FD',
  },
  methodLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#424242',
  },
  methodLabelSelected: {
    color: '#1976D2',
  },
  payButton: {
    backgroundColor: '#1976D2',
    paddingVertical: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  payButtonDisabled: {
    backgroundColor: '#9E9E9E',
  },
  payButtonLabel: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
  },
  loadingText: {
    marginTop: 16,
    fontSize: 14,
    color: '#757575',
  },
  resultIcon: {
    fontSize: 64,
    textAlign: 'center',
    marginBottom: 16,
    marginTop: 80,
  },
  resultTitle: {
    fontSize: 24,
    fontWeight: '700',
    color: '#1C1C1E',
    textAlign: 'center',
    marginBottom: 8,
  },
  resultDesc: {
    fontSize: 14,
    color: '#757575',
    textAlign: 'center',
    marginBottom: 40,
    lineHeight: 22,
  },
});
