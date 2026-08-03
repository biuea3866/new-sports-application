/**
 * 신청자 목록(개설자 전용) — A-R4
 *
 * 근거: design-fe-app.md "화면별 4상태 표" A-R4(0건 empty 정상 / 403 개설자만).
 *
 * 행은 신청 PK가 아니라 신청자를 식별해 표시한다 — 개설자가 알고 싶은 것은 "누가 신청했는가"
 * 이지 신청 행 번호가 아니다. 표시 이름은
 * `RecruitmentApplicantResponse.applicantDisplayName`(BE 가 user 컨텍스트에서 조합)을 쓰고,
 * 닉네임 미설정 계정은 `resolveDisplayName` 이 중립 기본값으로 대체한다 — 신청 PK(`신청 #5`)도
 * 사용자 PK(`신청자 #71`)도 노출하지 않는다(이전 open item 해소).
 */
import { FlatList, StyleSheet, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import axios from 'axios';

import { useApplications } from '../../../lib/useRecruitment';
import { APPLICATION_STATUS_LABEL, formatDateTimeMinute } from '../../../lib/recruitment-format';
import { Card, EmptyState, ErrorView, LoadingView, ThemedText } from '../../../components/ui';
import { useTheme } from '../../../theme/useTheme';
import { resolveDisplayName } from '../../../lib/displayName';
import type { RecruitmentApplicantResponse } from '../../../api/recruitment';

const EMPTY_MESSAGE = '아직 신청자가 없어요';
const FORBIDDEN_MESSAGE = '개설자만 볼 수 있어요';
const ERROR_MESSAGE = '신청자 목록을 불러오지 못했어요';

function ApplicationRow({ application }: { application: RecruitmentApplicantResponse }) {
  const appliedAt = formatDateTimeMinute(application.appliedAt);

  return (
    <Card style={styles.row}>
      <ThemedText variant="primary" style={styles.rowTitle}>
        {resolveDisplayName(application.applicantDisplayName)}
      </ThemedText>
      <ThemedText variant="secondary" style={styles.rowMeta}>
        {APPLICATION_STATUS_LABEL[application.status]} · {appliedAt}
      </ThemedText>
    </Card>
  );
}

export default function RecruitmentApplicationsScreen() {
  const { tokens } = useTheme();
  const { id } = useLocalSearchParams<{ id: string }>();
  const recruitmentId = Number(id ?? NaN);

  const { data, isLoading, isError, error, refetch } = useApplications(recruitmentId);
  const isForbidden = axios.isAxiosError(error) && error.response?.status === 403;
  const applications = data ?? [];

  return (
    <View style={[styles.container, { backgroundColor: tokens.background }]}>
      <ThemedText variant="primary" style={styles.title} accessibilityRole="header">
        신청자 목록
      </ThemedText>

      {isLoading ? (
        <LoadingView variant="skeleton" />
      ) : isForbidden ? (
        <EmptyState message={FORBIDDEN_MESSAGE} />
      ) : isError ? (
        <ErrorView message={ERROR_MESSAGE} onRetry={() => void refetch()} />
      ) : applications.length === 0 ? (
        <EmptyState message={EMPTY_MESSAGE} />
      ) : (
        <FlatList
          data={applications}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => <ApplicationRow application={item} />}
          contentContainerStyle={styles.list}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 16,
    paddingTop: 60,
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    marginBottom: 16,
  },
  list: {
    paddingBottom: 40,
  },
  row: {
    marginBottom: 10,
  },
  rowTitle: {
    fontSize: 15,
    fontWeight: '600',
  },
  rowMeta: {
    fontSize: 13,
    marginTop: 4,
  },
});
