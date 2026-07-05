/**
 * AirQualityBadge — 대기질 등급(grade)을 라벨·색으로 표시하는 프레젠테이션 컴포넌트.
 * 라벨은 FE-11 매핑(`lib/air-quality-format.ts`)을 재사용하고,
 * 색은 FE-10 테마 토큰(`theme/tokens.ts`의 airXxxBg/airXxxFg)을 useTheme()으로 경유한다.
 * UNKNOWN 등급은 표시할 정보가 없으므로 렌더하지 않는다.
 */
import { StyleSheet, Text, View } from 'react-native';
import type { AirQualityGrade } from '../api/types';
import { getAirQualityGradeDisplay } from '../lib/air-quality-format';
import { useTheme } from '../theme/useTheme';
import type { ThemeTokens } from '../theme/tokens';

export interface AirQualityBadgeProps {
  grade: AirQualityGrade;
}

interface BadgeTokenKeys {
  background: keyof ThemeTokens;
  foreground: keyof ThemeTokens;
}

const GRADE_TOKEN_KEYS: Record<AirQualityGrade, BadgeTokenKeys> = {
  GOOD: { background: 'airGoodBg', foreground: 'airGoodFg' },
  MODERATE: { background: 'airModerateBg', foreground: 'airModerateFg' },
  BAD: { background: 'airBadBg', foreground: 'airBadFg' },
  VERY_BAD: { background: 'airVeryBadBg', foreground: 'airVeryBadFg' },
  UNKNOWN: { background: 'airUnknownBg', foreground: 'airUnknownFg' },
};

export function AirQualityBadge({ grade }: AirQualityBadgeProps) {
  const { tokens } = useTheme();

  if (grade === 'UNKNOWN') {
    return null;
  }

  const { label } = getAirQualityGradeDisplay(grade);
  const { background, foreground } = GRADE_TOKEN_KEYS[grade];

  return (
    <View
      style={[styles.container, { backgroundColor: tokens[background] }]}
      accessibilityRole="text"
      accessibilityLabel={`대기질 등급 ${label}`}
    >
      <Text style={[styles.label, { color: tokens[foreground] }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignSelf: 'flex-start',
    paddingVertical: 4,
    paddingHorizontal: 10,
    borderRadius: 6,
  },
  label: {
    fontSize: 13,
    fontWeight: '600',
  },
});
