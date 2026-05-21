/**
 * 회원가입 화면 — placeholder
 * MOBILE-02 또는 AUTH 티켓에서 실제 구현 예정
 */
import { View, Text, StyleSheet } from 'react-native';

export default function RegisterScreen() {
  return (
    <View style={styles.container} accessible={true} accessibilityLabel="회원가입 화면">
      <Text style={styles.title}>회원가입</Text>
      <Text style={styles.placeholder} accessibilityRole="text">
        구현 예정 — AUTH 티켓 참조
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
