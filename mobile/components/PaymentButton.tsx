/**
 * PaymentButton — 결제 버튼
 *
 * 오프라인 상태에서 onPress를 차단하고 토스트 메시지를 표시합니다.
 * BE 결제 호출 자체는 이 컴포넌트 바깥의 onPress 핸들러가 담당합니다.
 */
import { TouchableOpacity, Text, StyleSheet, Alert } from 'react-native';
import { useNetInfo } from '../lib/netinfo';

interface PaymentButtonProps {
  label: string;
  onPress: () => void;
  disabled?: boolean;
}

export function PaymentButton({ label, onPress, disabled = false }: PaymentButtonProps) {
  const netInfo = useNetInfo();
  const isOffline = netInfo.isConnected === false;

  const handlePress = () => {
    if (isOffline) {
      Alert.alert('네트워크 연결 필요', '결제하려면 인터넷에 연결되어 있어야 합니다.');
      return;
    }
    onPress();
  };

  return (
    <TouchableOpacity
      style={[styles.button, (disabled || isOffline) && styles.buttonDisabled]}
      onPress={handlePress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled: disabled || isOffline }}
    >
      <Text style={styles.label}>{label}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  button: {
    backgroundColor: '#1976D2',
    paddingVertical: 14,
    paddingHorizontal: 24,
    borderRadius: 8,
    alignItems: 'center',
  },
  buttonDisabled: {
    backgroundColor: '#9E9E9E',
  },
  label: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
  },
});
