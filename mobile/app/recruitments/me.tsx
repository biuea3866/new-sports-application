/**
 * 내 신청 목록 — A-R5
 *
 * 근거: design-fe-app.md "텍스트 와이어프레임"/"화면별 4상태 표" A-R5, 컴포넌트 트리
 * "ApplicationCard → CancelApplicationSheet(A-R6)".
 *
 * 각 카드는 `useRecruitment(item.recruitmentId)`로 모집 제목·참가비를 함께 표시한다 —
 * `ApplicationResponse`에 제목·참가비가 없어(개설자/신청자 공용 응답) 카드 단위 조인이
 * 필요하다.
 */
import { useState } from 'react';
import { FlatList, StyleSheet, View } from 'react-native';

import type { ApplicationResponse } from '../../api/recruitment';
import { useMyApplications, useRecruitment } from '../../lib/useRecruitment';
import { APPLICATION_STATUS_LABEL, isApplicationCancellable } from '../../lib/recruitment-format';
import { CancelApplicationSheet } from '../../components/recruitment/CancelApplicationSheet';
import { Button, Card, EmptyState, ErrorView, LoadingView, ThemedText } from '../../components/ui';
import { useTheme } from '../../theme/useTheme';

const EMPTY_MESSAGE = '신청한 모집이 없어요';
const ERROR_MESSAGE = '신청 목록을 불러오지 못했어요';

interface ApplicationCardProps {
  application: ApplicationResponse;
  onRequestCancel: (application: ApplicationResponse) => void;
}

function ApplicationCard({ application, onRequestCancel }: ApplicationCardProps) {
  const { data: recruitment } = useRecruitment(application.recruitmentId);

  return (
    <Card style={styles.card}>
      <ThemedText variant="primary" style={styles.cardTitle} numberOfLines={1}>
        {recruitment?.title ?? `모집 #${application.recruitmentId}`}
      </ThemedText>
      <ThemedText variant="secondary" style={styles.cardMeta}>
        {APPLICATION_STATUS_LABEL[application.status]}
      </ThemedText>

      {isApplicationCancellable(application.status) ? (
        <View style={styles.cancelButtonWrapper}>
          <Button label="취소" variant="surface" onPress={() => onRequestCancel(application)} />
        </View>
      ) : null}
    </Card>
  );
}

export default function MyApplicationsScreen() {
  const { tokens } = useTheme();
  const { data, isLoading, isError, refetch } = useMyApplications();
  const [cancelTarget, setCancelTarget] = useState<ApplicationResponse | null>(null);

  const applications = data ?? [];

  return (
    <View style={[styles.container, { backgroundColor: tokens.background }]}>
      <ThemedText variant="primary" style={styles.title} accessibilityRole="header">
        내 신청
      </ThemedText>

      {isLoading ? (
        <LoadingView variant="skeleton" />
      ) : isError ? (
        <ErrorView message={ERROR_MESSAGE} onRetry={() => void refetch()} />
      ) : applications.length === 0 ? (
        <EmptyState message={EMPTY_MESSAGE} />
      ) : (
        <FlatList
          data={applications}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <ApplicationCard application={item} onRequestCancel={setCancelTarget} />
          )}
          contentContainerStyle={styles.list}
        />
      )}

      {cancelTarget !== null ? (
        <CancelApplicationSheet application={cancelTarget} onClose={() => setCancelTarget(null)} />
      ) : null}
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
    fontSize: 24,
    fontWeight: '700',
    marginBottom: 16,
  },
  list: {
    paddingBottom: 40,
  },
  card: {
    marginBottom: 12,
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '600',
  },
  cardMeta: {
    fontSize: 13,
    marginTop: 4,
  },
  cancelButtonWrapper: {
    marginTop: 12,
    alignSelf: 'flex-start',
    minWidth: 100,
  },
});
