/**
 * 예약 목록 화면 — placeholder
 * 후행 티켓에서 실제 구현 예정
 */
import { View, Text, StyleSheet } from 'react-native';

export default function BookingListScreen() {
  return (
    <View style={styles.container} accessible={true} accessibilityLabel="예약 목록 화면">
      <Text style={styles.title}>예약 목록</Text>
      <Text style={styles.placeholder} accessibilityRole="text">
        준비중
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1C1C1E',
  },
  placeholder: {
    fontSize: 14,
    color: '#8E8E93',
    marginTop: 12,
  },
});
