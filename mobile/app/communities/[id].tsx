/**
 * 커뮤니티 상세·가입·멤버·역할 관리 화면 (S5)
 * 근거: FE-12 티켓, design-fe-app.md S5 와이어프레임·"화면별 상태 표"·"권한별".
 *
 * 토스 패턴: 상단 요약 + 명확한 단일 주요 CTA(가입/채팅 입장), 멤버는 하위 섹션.
 * `GET /communities/{id}/members`는 ACTIVE 멤버만 반환하고 비ACTIVE 요청자에게는 403을
 * 반환한다(FR-13 ②, 서버 강제) — 이 화면은 UI 게이팅에 의존하지 않고 403 응답을 그대로
 * "멤버 목록 접근 제한" 안내로 렌더한다.
 */
import { useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import axios from 'axios';

import type { CommunityMemberResponse } from '../../api/community-types';
import { CommunityMemberList } from '../../components/community/CommunityMemberList';
import {
  canManageMembers,
  resolveViewerMembership,
} from '../../components/community/communityRole';
import { CommunitySummary } from '../../components/community/CommunitySummary';
import { EmptyState, ErrorView, LoadingView, ThemedText, ThemedView } from '../../components/ui';
import {
  useCommunity,
  useCommunityMembers,
  useJoinCommunity,
  useKickMember,
  useLeaveCommunity,
  useTransferHost,
} from '../../lib/useCommunity';
import { useMyProfile } from '../../lib/useMyProfile';
import { MY_ROOMS_QUERY_KEY } from '../../lib/useRooms';

const MEMBERS_RESTRICTED_MESSAGE = '멤버 목록은 가입한 멤버만 볼 수 있어요';

function isForbiddenError(error: Error | null): boolean {
  return axios.isAxiosError(error) && error.response?.status === 403;
}

export default function CommunityDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const communityId = Number(id ?? NaN);
  const queryClient = useQueryClient();

  const [isPendingApproval, setIsPendingApproval] = useState(false);

  const communityQuery = useCommunity(communityId);
  const membersQuery = useCommunityMembers(communityId);
  const myProfileQuery = useMyProfile();

  const joinMutation = useJoinCommunity(communityId);
  const kickMutation = useKickMember(communityId);
  const transferHostMutation = useTransferHost(communityId);
  const leaveMutation = useLeaveCommunity(communityId);

  const myUserId = myProfileQuery.data?.id ?? -1;
  const members = membersQuery.data ?? [];
  const membersForbidden = isForbiddenError(membersQuery.error);
  const viewer = resolveViewerMembership({ members, myUserId, isPendingApproval });

  function handleJoin() {
    joinMutation.mutate(undefined, {
      onSuccess: (result) => {
        setIsPendingApproval(result.status === 'PENDING_APPROVAL');
      },
    });
  }

  function handleEnterChat() {
    const roomId = communityQuery.data?.roomId;
    if (roomId === null || roomId === undefined) {
      return;
    }
    router.push(`/rooms/${roomId}`);
  }

  function handleKick(member: CommunityMemberResponse) {
    Alert.alert('멤버 강퇴', `사용자 #${member.userId}님을 강퇴할까요?`, [
      { text: '취소', style: 'cancel' },
      {
        text: '강퇴',
        style: 'destructive',
        onPress: () => {
          kickMutation.mutate(
            { userId: member.userId },
            {
              onSuccess: () => {
                void queryClient.invalidateQueries({ queryKey: MY_ROOMS_QUERY_KEY });
              },
            }
          );
        },
      },
    ]);
  }

  function handleTransfer(member: CommunityMemberResponse) {
    Alert.alert('방장 위임', `사용자 #${member.userId}님에게 방장을 위임할까요?`, [
      { text: '취소', style: 'cancel' },
      {
        text: '위임',
        onPress: () => transferHostMutation.mutate({ newHostUserId: member.userId }),
      },
    ]);
  }

  function handleLeave() {
    Alert.alert('커뮤니티 탈퇴', '탈퇴하면 채팅방에서도 자동으로 나가게 돼요. 탈퇴할까요?', [
      { text: '취소', style: 'cancel' },
      { text: '탈퇴', style: 'destructive', onPress: () => leaveMutation.mutate() },
    ]);
  }

  const isLoading = communityQuery.isLoading || membersQuery.isLoading || myProfileQuery.isLoading;

  return (
    <ThemedView style={styles.container} background="background">
      {isLoading && <LoadingView variant="skeleton" skeletonCount={4} />}

      {!isLoading && communityQuery.isError && (
        <ErrorView
          message="커뮤니티 정보를 불러오지 못했어요"
          onRetry={() => communityQuery.refetch()}
        />
      )}

      {!isLoading && !communityQuery.isError && communityQuery.data === undefined && (
        <EmptyState message="커뮤니티를 찾을 수 없어요" />
      )}

      {!isLoading && !communityQuery.isError && communityQuery.data !== undefined && (
        <ScrollView contentContainerStyle={styles.content}>
          <CommunitySummary
            community={communityQuery.data}
            viewer={viewer}
            onJoin={handleJoin}
            onEnterChat={handleEnterChat}
            isJoinPending={joinMutation.isPending}
          />

          <ThemedView style={styles.membersSection} background="background">
            {membersForbidden && <EmptyState message={MEMBERS_RESTRICTED_MESSAGE} />}

            {!membersForbidden && membersQuery.isError && (
              <ErrorView
                message="멤버 목록을 불러오지 못했어요"
                onRetry={() => membersQuery.refetch()}
              />
            )}

            {!membersQuery.isError && (
              <CommunityMemberList
                members={members}
                canManage={canManageMembers(viewer)}
                onKick={handleKick}
                onTransfer={handleTransfer}
              />
            )}
          </ThemedView>

          {(viewer.kind === 'member' || viewer.kind === 'host') && (
            <Pressable
              style={styles.leaveButton}
              onPress={handleLeave}
              accessibilityRole="button"
              accessibilityLabel="탈퇴하기"
            >
              <ThemedText variant="danger" style={styles.leaveText}>
                탈퇴하기
              </ThemedText>
            </Pressable>
          )}
        </ScrollView>
      )}
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    padding: 16,
    paddingBottom: 40,
  },
  membersSection: {
    marginTop: 24,
  },
  leaveButton: {
    marginTop: 24,
    alignItems: 'center',
    paddingVertical: 12,
  },
  leaveText: {
    fontSize: 15,
    fontWeight: '600',
  },
});
