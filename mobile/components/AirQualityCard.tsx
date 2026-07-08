/**
 * AirQualityCard — 시설 상세의 대기질 정보 카드. 4상태(loading/error/success + pm 결측)를
 * 표시하는 프레젠테이션 컴포넌트. fetch·query 훅 호출은 하지 않고 props로만 데이터를 받는다.
 * BE는 조회 실패 시에도 200 + representativeGrade="UNKNOWN"으로 응답하므로(FE-11 계약),
 * success 상태에서도 UNKNOWN·pm 모두 null이면 폴백 문구를 보여준다.
 */
import { StyleSheet, Text, View } from 'react-native';
import type { AirQualityResponse } from '../api/types';
import { useTheme } from '../theme/useTheme';
import { AirQualityBadge } from './AirQualityBadge';

export type AirQualityCardStatus = 'loading' | 'error' | 'success';

export interface AirQualityCardProps {
  status: AirQualityCardStatus;
  data: AirQualityResponse | null;
}

function isAirQualityUnavailable(data: AirQualityResponse): boolean {
  return data.representativeGrade === 'UNKNOWN' || (data.pm10 === null && data.pm25 === null);
}

function formatMeasuredTime(measuredAt: string): string | null {
  const [, timePart] = measuredAt.split('T');
  if (!timePart || timePart.length < 5) {
    return null;
  }
  return timePart.slice(0, 5);
}

export function AirQualityCard({ status, data }: AirQualityCardProps) {
  const { tokens } = useTheme();

  if (status === 'loading') {
    return (
      <View style={[styles.container, { backgroundColor: tokens.surface }]}>
        <Text style={[styles.message, { color: tokens.textSecondary }]}>
          대기질 정보를 불러오는 중…
        </Text>
      </View>
    );
  }

  if (status === 'error' || data === null || isAirQualityUnavailable(data)) {
    return (
      <View style={[styles.container, { backgroundColor: tokens.surface }]}>
        <Text style={[styles.message, { color: tokens.textSecondary }]}>
          대기질 정보를 불러올 수 없습니다
        </Text>
      </View>
    );
  }

  const measuredTime = data.measuredAt ? formatMeasuredTime(data.measuredAt) : null;
  const metaText = [data.stationName, measuredTime].filter(Boolean).join('·');

  return (
    <View style={[styles.container, { backgroundColor: tokens.surface }]}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: tokens.textPrimary }]}>현재 대기질</Text>
        <AirQualityBadge grade={data.representativeGrade} />
      </View>
      {data.pm10 !== null ? (
        <Text style={[styles.value, { color: tokens.textPrimary }]}>PM10 {data.pm10} ㎍/㎥</Text>
      ) : null}
      {data.pm25 !== null ? (
        <Text style={[styles.value, { color: tokens.textPrimary }]}>PM2.5 {data.pm25} ㎍/㎥</Text>
      ) : null}
      {metaText.length > 0 ? (
        <Text style={[styles.meta, { color: tokens.textSecondary }]}>{metaText}</Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    borderRadius: 12,
    padding: 16,
    gap: 4,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
  title: {
    fontSize: 15,
    fontWeight: '600',
  },
  value: {
    fontSize: 14,
  },
  meta: {
    fontSize: 12,
    marginTop: 4,
  },
  message: {
    fontSize: 14,
  },
});
