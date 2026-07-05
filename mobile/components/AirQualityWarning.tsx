/**
 * AirQualityWarning — 예약 전 대기질 나쁨 이상(BAD/VERY_BAD) 경고 배너.
 * `isBadOrWorse(grade)`(FE-11)가 true일 때만 렌더하고, GOOD/MODERATE/UNKNOWN이면
 * 아무것도 렌더하지 않는다(null). 색은 useTheme(FE-10)의 danger/airBad 토큰을 경유한다.
 *
 * 확인(Confirm) 인터랙션은 이 컴포넌트가 갖지 않는다 — 부모(예약 화면)가 자신의
 * CTA 버튼 라벨을 전환해 처리한다(FE-14 티켓 단순화 결정). 이 컴포넌트는 경고 표시만 전담한다.
 */
import { StyleSheet, Text, View } from 'react-native';
import type { AirQualityGrade } from '../api/types';
import { getAirQualityGradeDisplay, isBadOrWorse } from '../lib/air-quality-format';
import { useTheme } from '../theme/useTheme';

export interface AirQualityWarningProps {
  grade: AirQualityGrade;
  pm10: number | null;
}

function buildWarningMessage(grade: AirQualityGrade, pm10: number | null): string {
  const { label } = getAirQualityGradeDisplay(grade);
  if (pm10 === null) {
    return `현재 대기질이 ${label}입니다. 야외 활동에 유의하세요`;
  }
  return `현재 대기질이 ${label}입니다 (PM10 ${pm10}). 야외 활동에 유의하세요`;
}

export function AirQualityWarning({ grade, pm10 }: AirQualityWarningProps) {
  const { tokens } = useTheme();

  if (!isBadOrWorse(grade)) {
    return null;
  }

  return (
    <View
      style={[styles.container, { backgroundColor: tokens.airBadBg }]}
      accessible
      accessibilityRole="alert"
    >
      <Text style={[styles.message, { color: tokens.danger }]}>
        {buildWarningMessage(grade, pm10)}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    borderRadius: 12,
    padding: 12,
  },
  message: {
    fontSize: 14,
    fontWeight: '600',
  },
});
