/**
 * WaitingRoomScreen — S1 대기실 화면 (컨테이너)
 *
 * 근거: 티켓 FE-07, `20260709-가상대기열-design-fe-app.md` "S1 텍스트 와이어프레임" ·
 * "화면별 상태 표" · "컴포넌트 트리".
 *
 * 로직은 전부 useWaitingRoom 뷰모델 훅에 있다. 이 컴포넌트는 phase별 렌더만 담당한다
 * (no-logic-in-component). 헤더 '나가기'와 언마운트 시 leaveQueue를 best-effort 호출한다.
 */
import { useEffect } from 'react';
import { ScrollView, StyleSheet, TouchableOpacity } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { Button } from '../../../components/ui/Button';
import { EmptyState } from '../../../components/ui/EmptyState';
import { ErrorView } from '../../../components/ui/ErrorView';
import { LoadingView } from '../../../components/ui/LoadingView';
import { ThemedText } from '../../../components/themed/ThemedText';
import { ThemedView } from '../../../components/themed/ThemedView';
import { QueuePositionCard } from '../../../components/virtualQueue/QueuePositionCard';
import { QueueProgressBar } from '../../../components/virtualQueue/QueueProgressBar';
import { QueueWaitInfo } from '../../../components/virtualQueue/QueueWaitInfo';
import type { QueueTargetType } from '../../../api/virtualQueue';
import { useWaitingRoom } from '../../../lib/useWaitingRoom';

/** 라우트 파라미터 `type`은 문자열로 들어오므로, 계약 밖 값은 기본값(limited-drop)으로 좁힌다. */
function toQueueTargetType(rawType: string | string[] | undefined): QueueTargetType {
  return rawType === 'ticketing-event' ? 'ticketing-event' : 'limited-drop';
}

export default function WaitingRoomScreen() {
  const { type, targetId } = useLocalSearchParams<{ type: string; targetId: string }>();
  const router = useRouter();
  const targetType = toQueueTargetType(type);
  const numericTargetId = Number(targetId);

  const viewModel = useWaitingRoom(targetType, numericTargetId);

  // 화면 이탈(언마운트) 시에도 best-effort로 대기열에서 나간다.
  useEffect(() => {
    return () => {
      viewModel.leave();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleExit = () => {
    viewModel.leave();
    router.back();
  };

  return (
    <ThemedView style={styles.container}>
      <ThemedView style={styles.header}>
        <TouchableOpacity
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="닫기"
        >
          <ThemedText variant="secondary">닫기</ThemedText>
        </TouchableOpacity>
        <TouchableOpacity
          onPress={handleExit}
          accessibilityRole="button"
          accessibilityLabel="나가기"
        >
          <ThemedText variant="secondary">나가기</ThemedText>
        </TouchableOpacity>
      </ThemedView>

      <ScrollView contentContainerStyle={styles.content}>
        {viewModel.phase === 'loading' && (
          <ThemedView style={styles.center} accessible accessibilityLabel="대기열에 들어가는 중">
            <LoadingView />
            <ThemedText variant="secondary" style={styles.spacingTop}>
              대기열에 들어가는 중
            </ThemedText>
          </ThemedView>
        )}

        {viewModel.phase === 'waiting' && (
          <ThemedView style={styles.center}>
            <ThemedText variant="secondary" style={styles.spacingBottom}>
              잠시만 기다려 주세요
            </ThemedText>

            {viewModel.position !== null && <QueuePositionCard position={viewModel.position} />}

            {viewModel.ratio !== null && viewModel.percentLabel !== null && (
              <ThemedView style={styles.progressRow}>
                <QueueProgressBar ratio={viewModel.ratio} percentLabel={viewModel.percentLabel} />
              </ThemedView>
            )}

            <QueueWaitInfo aheadCount={viewModel.aheadCount} etaLabel={viewModel.etaLabel} />

            <ThemedText variant="muted" style={styles.hint}>
              접속이 많아 순서대로 입장하고 있어요
            </ThemedText>
          </ThemedView>
        )}

        {viewModel.phase === 'admitted' && (
          <ThemedView style={styles.center} accessible accessibilityLabel="입장! 이동 중">
            <LoadingView />
            <ThemedText variant="secondary" style={styles.spacingTop}>
              입장! 이동 중
            </ThemedText>
          </ThemedView>
        )}

        {viewModel.phase === 'empty' && (
          <ThemedView style={styles.center}>
            <EmptyState
              message="대기열에서 나왔어요"
              description="자리를 비운 사이 순번이 정리됐어요"
            />
            <ThemedView style={styles.ctaRow}>
              <Button label="다시 대기" onPress={viewModel.retry} />
            </ThemedView>
          </ThemedView>
        )}

        {viewModel.phase === 'full' && (
          <ThemedView style={styles.center}>
            <EmptyState
              message="지금 대기 인원이 많아요"
              description="잠시 후 다시 시도해 주세요"
            />
            <ThemedView style={styles.ctaRow}>
              <Button label="다시 시도" onPress={viewModel.retry} />
            </ThemedView>
          </ThemedView>
        )}

        {viewModel.phase === 'error' && (
          <ErrorView message="연결이 불안정해요" onRetry={viewModel.retry} />
        )}
      </ScrollView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  content: {
    flexGrow: 1,
    padding: 20,
    paddingBottom: 40,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 24,
  },
  progressRow: {
    width: '100%',
    marginTop: 20,
  },
  hint: {
    marginTop: 24,
    fontSize: 13,
    textAlign: 'center',
  },
  spacingTop: {
    marginTop: 12,
  },
  spacingBottom: {
    marginBottom: 12,
  },
  ctaRow: {
    marginTop: 16,
    minWidth: 160,
  },
});
