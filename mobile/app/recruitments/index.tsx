/**
 * 모집 목록 화면 — A-R1
 *
 * 근거: `20260707-모집-시설상품-소모임예약-게시글연동-design-fe-app.md` "텍스트 와이어프레임"
 * A-R1(토스 피드 패턴 — 절제된 카드 리스트 + 플로팅 단일 CTA). `useRecruitments`(foundation
 * `lib/useRecruitment.ts`)로 모집 목록을 조회한다.
 *
 * 세그먼트 필터: 진입 시 `communityId` 라우트 파라미터가 있으면 [전체]/[우리 모임] 세그먼트를
 * 보여준다(파라미터가 없는 전역 진입은 필터할 대상 모임이 없어 세그먼트를 숨긴다).
 */
import { useState } from 'react';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';

import { RecruitmentCard } from '../../components/recruitment/RecruitmentCard';
import {
  EmptyState,
  ErrorView,
  LoadingView,
  SegmentedControl,
  ThemedText,
} from '../../components/ui';
import { useTheme } from '../../theme/useTheme';
import { useRecruitments } from '../../lib/useRecruitment';

const EMPTY_MESSAGE = '아직 모집이 없어요';
const EMPTY_DESCRIPTION = '첫 모집을 개설해보세요';
const ERROR_MESSAGE = '모집 목록을 불러오지 못했습니다.';

type Segment = 'ALL' | 'MINE';

const SEGMENT_OPTIONS = [
  { label: '전체', value: 'ALL' },
  { label: '우리 모임', value: 'MINE' },
];

function isSegment(value: string): value is Segment {
  return value === 'ALL' || value === 'MINE';
}

export default function RecruitmentsListScreen() {
  const { tokens } = useTheme();
  const params = useLocalSearchParams<{ communityId?: string }>();
  const scopedCommunityId =
    typeof params.communityId === 'string' ? Number(params.communityId) : null;

  const [segment, setSegment] = useState<Segment>('ALL');
  const activeCommunityId =
    segment === 'MINE' && scopedCommunityId !== null ? scopedCommunityId : undefined;

  const { data, isLoading, isError, refetch } = useRecruitments(activeCommunityId);
  const recruitments = data ?? [];

  const handleSegmentChange = (value: string) => {
    if (isSegment(value)) {
      setSegment(value);
    }
  };

  return (
    <View style={[styles.container, { backgroundColor: tokens.background }]}>
      <ThemedText variant="primary" style={styles.title} accessibilityRole="header">
        모집
      </ThemedText>

      {scopedCommunityId !== null ? (
        <View style={styles.segmentWrapper}>
          <SegmentedControl
            options={SEGMENT_OPTIONS}
            value={segment}
            onChange={handleSegmentChange}
          />
        </View>
      ) : null}

      <View style={styles.body}>
        {isLoading ? (
          <LoadingView variant="skeleton" />
        ) : isError ? (
          <ErrorView message={ERROR_MESSAGE} onRetry={() => void refetch()} />
        ) : recruitments.length === 0 ? (
          <EmptyState message={EMPTY_MESSAGE} description={EMPTY_DESCRIPTION} />
        ) : (
          <FlatList
            data={recruitments}
            keyExtractor={(item) => String(item.id)}
            renderItem={({ item }) => (
              <RecruitmentCard
                recruitment={item}
                onPress={() => router.push(`/recruitments/${item.id}`)}
              />
            )}
            contentContainerStyle={styles.list}
          />
        )}
      </View>

      <Pressable
        style={[styles.floatingCta, { backgroundColor: tokens.accent }]}
        onPress={() => router.push('/recruitments/new')}
        accessibilityRole="button"
        accessibilityLabel="모집 개설"
      >
        <ThemedText variant="onAccent" style={styles.floatingCtaLabel}>
          + 개설
        </ThemedText>
      </Pressable>
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
  segmentWrapper: {
    marginBottom: 16,
  },
  body: {
    flex: 1,
  },
  list: {
    paddingBottom: 96,
  },
  floatingCta: {
    position: 'absolute',
    right: 20,
    bottom: 32,
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderRadius: 24,
  },
  floatingCtaLabel: {
    fontSize: 15,
    fontWeight: '700',
  },
});
