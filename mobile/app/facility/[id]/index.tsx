/**
 * 시설 상세 화면 — MO-07
 * GET /facilities/{id} (public)
 *
 * FE-15: 시/도 표시(구 위) + 대기질 카드(FE-12 훅·FE-13 컴포넌트) 통합.
 * 시설 미존재(에러 아님, data undefined) empty 분기 추가.
 * 좌표(lat/lng)가 없으면(레거시 데이터 방어) 대기질 카드는 렌더하지 않는다.
 *
 * FE-28(A-F1): 시설상품(program) 목록 섹션 추가 — `facility.program.enabled` 플래그
 * ON일 때만 `usePrograms(facilityId)`로 조회해 렌더한다(design-fe-app.md "텍스트
 * 와이어프레임" A-F1, "화면별 4상태 표"). 카드 탭 시 `/booking/new?programId=`(A-F2)로 이동한다.
 */
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
} from 'react-native';
import { useLocalSearchParams, router } from 'expo-router';
import { useFacilityDetail } from '../../../lib/useFacility';
import { useAirQuality } from '../../../lib/useAirQuality';
import { usePrograms } from '../../../lib/useProgram';
import { isFeatureEnabled } from '../../../lib/feature-flags';
import { AirQualityCard, type AirQualityCardStatus } from '../../../components/AirQualityCard';
import { ProgramCard } from '../../../components/facility/ProgramCard';
import { EmptyState, ErrorView, LoadingView, ThemedText } from '../../../components/ui';
import type { FacilityResponse, FacilityType } from '../../../api/types';
import { useTheme } from '../../../theme/useTheme';
import { createStyles } from '../../../theme/createStyles';
import type { ThemeTokens } from '../../../theme/tokens';

const TYPE_LABEL: Record<FacilityType, string> = {
  INDOOR: '실내',
  OUTDOOR: '실외',
  MIXED: '복합',
};

const REGION_UNKNOWN_LABEL = '지역 미확인';
// BE는 주소 파싱에 실패한 시설의 시도명을 "미지정"으로 보존한다
// (FacilityRegion.UNSPECIFIED, backend/domain/facility/vo/FacilityRegion.kt).
// 웹(web/app/portal/facilities/sido-display.ts#resolveSidoDisplayName)과 동일하게
// 값이 없거나 "미지정"이면 "지역 미확인"으로 표시해 플랫폼 표기를 통일한다.
const REGION_UNSPECIFIED_SENTINEL = '미지정';

function resolveSidoLabel(sidoName: FacilityResponse['sidoName'] | undefined): string {
  if (sidoName === undefined || sidoName === null) {
    return REGION_UNKNOWN_LABEL;
  }
  const trimmed = sidoName.trim();
  if (trimmed.length === 0 || trimmed === REGION_UNSPECIFIED_SENTINEL) {
    return REGION_UNKNOWN_LABEL;
  }
  return sidoName;
}

function resolveCoordinate(value: number | null | undefined): number | null {
  return value === null || value === undefined ? null : value;
}

export default function FacilityDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const facilityId = id ?? '';
  const { tokens } = useTheme();
  const styles = useStyles(tokens);

  const { data, isLoading, isError, error } = useFacilityDetail(facilityId);

  const lat = resolveCoordinate(data?.lat);
  const lng = resolveCoordinate(data?.lng);
  const hasCoordinates = lat !== null && lng !== null;

  const airQuality = useAirQuality(hasCoordinates ? lat : null, hasCoordinates ? lng : null);
  const airQualityStatus: AirQualityCardStatus = airQuality.isError
    ? 'error'
    : airQuality.isLoading
      ? 'loading'
      : 'success';

  const isProgramSectionEnabled = isFeatureEnabled('facility.program.enabled');
  const {
    data: programs,
    isLoading: isProgramsLoading,
    isError: isProgramsError,
    refetch: refetchPrograms,
  } = usePrograms(isProgramSectionEnabled ? facilityId : '');

  const isEmpty = !isLoading && !isError && data === undefined;

  return (
    <View style={styles.container}>
      <TouchableOpacity
        style={styles.backButton}
        onPress={() => router.back()}
        accessible={true}
        accessibilityLabel="뒤로 가기"
        accessibilityRole="button"
      >
        <Text style={styles.backText}>{'< 뒤로'}</Text>
      </TouchableOpacity>

      {isLoading && (
        <View style={styles.centerBox} accessible={true} accessibilityLabel="로딩 중">
          <ActivityIndicator size="large" color={tokens.accent} />
        </View>
      )}

      {isError && (
        <View style={styles.centerBox} accessible={true} accessibilityLabel="오류 발생">
          <Text style={styles.errorText}>
            {error instanceof Error ? error.message : '시설 정보를 불러오지 못했습니다.'}
          </Text>
        </View>
      )}

      {isEmpty && (
        <View style={styles.centerBox} accessible={true} accessibilityLabel="시설 없음">
          <Text style={styles.errorText}>시설을 찾을 수 없습니다</Text>
        </View>
      )}

      {!isLoading && !isError && data !== undefined && (
        <>
          <ScrollView
            contentContainerStyle={styles.scrollContent}
            accessible={false}
            accessibilityLabel="시설 상세 정보"
          >
            <Text style={styles.name}>{data.name}</Text>
            <View style={styles.row}>
              <Text style={styles.label}>시/도</Text>
              <Text style={styles.value}>{resolveSidoLabel(data.sidoName)}</Text>
            </View>
            <View style={styles.row}>
              <Text style={styles.label}>구</Text>
              <Text style={styles.value}>{data.gu}</Text>
            </View>
            <View style={styles.row}>
              <Text style={styles.label}>타입</Text>
              <Text style={styles.value}>{TYPE_LABEL[data.type]}</Text>
            </View>
            <View style={styles.row}>
              <Text style={styles.label}>주소</Text>
              <Text style={styles.value}>{data.address}</Text>
            </View>
            <View style={styles.row}>
              <Text style={styles.label}>주차</Text>
              <Text style={styles.value}>{data.parking ? '가능' : '불가'}</Text>
            </View>
            {data.tel.length > 0 && (
              <View style={styles.row}>
                <Text style={styles.label}>전화</Text>
                <Text style={[styles.value, styles.phone]}>{data.tel}</Text>
              </View>
            )}
            {hasCoordinates && (
              <View style={styles.airQualitySection}>
                <AirQualityCard status={airQualityStatus} data={airQuality.data ?? null} />
              </View>
            )}
            {isProgramSectionEnabled && (
              <View style={styles.programSection}>
                <ThemedText
                  variant="primary"
                  style={styles.programSectionTitle}
                  accessibilityRole="header"
                >
                  시설상품
                </ThemedText>
                {isProgramsLoading ? (
                  <LoadingView variant="skeleton" skeletonCount={2} />
                ) : isProgramsError ? (
                  <ErrorView
                    message="시설상품을 불러오지 못했습니다."
                    onRetry={() => void refetchPrograms()}
                  />
                ) : (programs ?? []).length === 0 ? (
                  <EmptyState message="등록된 상품이 없어요" />
                ) : (
                  (programs ?? []).map((program) => (
                    <ProgramCard
                      key={program.id}
                      program={program}
                      onPress={() =>
                        router.push(`/booking/new?facilityId=${facilityId}&programId=${program.id}`)
                      }
                    />
                  ))
                )}
              </View>
            )}
          </ScrollView>
          <View style={styles.bookingButtonContainer}>
            <TouchableOpacity
              style={styles.bookingButton}
              onPress={() => router.push(`/booking/new?facilityId=${facilityId}`)}
              accessibilityRole="button"
              accessibilityLabel="예약하기"
            >
              <Text style={styles.bookingButtonText}>예약하기</Text>
            </TouchableOpacity>
          </View>
        </>
      )}
    </View>
  );
}

const useStyles = createStyles((theme: ThemeTokens) =>
  StyleSheet.create({
    container: {
      flex: 1,
      backgroundColor: theme.background,
    },
    backButton: {
      paddingHorizontal: 16,
      paddingTop: 56,
      paddingBottom: 12,
      backgroundColor: theme.surface,
    },
    backText: {
      fontSize: 16,
      color: theme.accent,
    },
    centerBox: {
      flex: 1,
      alignItems: 'center',
      justifyContent: 'center',
      padding: 24,
    },
    errorText: {
      fontSize: 14,
      color: theme.danger,
      textAlign: 'center',
    },
    scrollContent: {
      padding: 16,
    },
    name: {
      fontSize: 22,
      fontWeight: '700',
      color: theme.textPrimary,
      marginBottom: 20,
    },
    row: {
      flexDirection: 'row',
      paddingVertical: 12,
      borderBottomWidth: StyleSheet.hairlineWidth,
      borderBottomColor: theme.disabled,
    },
    label: {
      width: 56,
      fontSize: 14,
      color: theme.textMuted,
    },
    value: {
      flex: 1,
      fontSize: 14,
      color: theme.textPrimary,
    },
    phone: {
      color: theme.accent,
    },
    airQualitySection: {
      marginTop: 16,
    },
    programSection: {
      marginTop: 20,
    },
    programSectionTitle: {
      fontSize: 16,
      fontWeight: '700',
      marginBottom: 12,
    },
    bookingButtonContainer: {
      padding: 16,
      backgroundColor: theme.surface,
      borderTopWidth: StyleSheet.hairlineWidth,
      borderTopColor: theme.disabled,
    },
    bookingButton: {
      backgroundColor: theme.accent,
      borderRadius: 12,
      paddingVertical: 16,
      alignItems: 'center',
    },
    bookingButtonText: {
      color: theme.accentText,
      fontSize: 16,
      fontWeight: '700',
    },
  })
);
