/**
 * 커뮤니티 상세·가입·멤버·역할 관리·게시판·소모임예약 화면 (S5 확장)
 * 근거: FE-12 티켓, design-fe-app.md S5 와이어프레임·"화면별 상태 표"·"권한별",
 * `20260707-모집-시설상품-소모임예약-게시글연동-design-fe-app.md` "텍스트 와이어프레임"
 * A-P1/A-P2(모임 게시판)·A-B1(소모임 예약)·"컴포넌트 트리"(CommunitySegmentTabs
 * [소개|멤버|게시판|활동]).
 *
 * 토스 패턴: 상단 요약 + 명확한 단일 주요 CTA(가입/채팅 입장), 세그먼트 탭으로 하위 콘텐츠
 * 분리(소개/멤버/게시판/활동). `GET /communities/{id}/members`는 ACTIVE 멤버만 반환하고
 * 비ACTIVE 요청자에게는 403을 반환한다(FR-13 ②, 서버 강제) — 이 화면은 UI 게이팅에 의존하지
 * 않고 403 응답을 그대로 "멤버 목록 접근 제한" 안내로 렌더한다. 게시판·소모임예약 섹션도
 * 동일 패턴(서버 403 → 잠금 UI)을 따른다.
 *
 * 게시판(community.post.enabled)·활동(community.booking.enabled) 탭은 기능 플래그가 꺼져
 * 있으면 탭 자체를 렌더하지 않는다(design-fe-app "Release Scenario — 점진 공개").
 */
import { useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';

import type { CommunityMemberResponse } from '../../api/community-types';
import { CommunityBoardSection } from '../../components/community/CommunityBoardSection';
import { CommunityBookingSection } from '../../components/community/CommunityBookingSection';
import { CommunityMemberList } from '../../components/community/CommunityMemberList';
import {
  canManageMembers,
  resolveViewerMembership,
} from '../../components/community/communityRole';
import { CommunitySummary } from '../../components/community/CommunitySummary';
import {
  EmptyState,
  ErrorView,
  LoadingView,
  SegmentedControl,
  ThemedText,
  ThemedView,
} from '../../components/ui';
import { ScreenHeader } from '../../components/common/ScreenHeader';
import { isFeatureEnabled } from '../../lib/feature-flags';
import { isForbiddenError } from '../../lib/http-error';
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

type CommunityDetailTab = 'intro' | 'members' | 'board' | 'booking';

const TAB_LABEL: Record<CommunityDetailTab, string> = {
  intro: '소개',
  members: '멤버',
  board: '게시판',
  booking: '활동',
};

export default function CommunityDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const communityId = Number(id ?? NaN);
  const queryClient = useQueryClient();

  const [isPendingApproval, setIsPendingApproval] = useState(false);
  const [activeTab, setActiveTab] = useState<CommunityDetailTab>('intro');

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
  const isInvalidCommunityId = Number.isNaN(communityId);

  const isPostEnabled = isFeatureEnabled('community.post.enabled');
  const isBookingEnabled = isFeatureEnabled('community.booking.enabled');
  const canWritePost = viewer.kind === 'member' || viewer.kind === 'host';
  const canLinkBooking = viewer.kind === 'host';

  const visibleTabs: CommunityDetailTab[] = [
    'intro',
    'members',
    ...(isPostEnabled ? (['board'] as const) : []),
    ...(isBookingEnabled ? (['booking'] as const) : []),
  ];
  const tabOptions = visibleTabs.map((tab) => ({ label: TAB_LABEL[tab], value: tab }));

  function handleTabChange(value: string) {
    if (visibleTabs.includes(value as CommunityDetailTab)) {
      setActiveTab(value as CommunityDetailTab);
    }
  }

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
      Alert.alert('채팅방 안내', '채팅방이 아직 없습니다');
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

  function handleCreatePost() {
    router.push(`/community/new?communityId=${communityId}`);
  }

  function handlePostPress(postId: number) {
    router.push(`/community/${postId}`);
  }

  function handleLinkBooking() {
    router.push(`/communities/${communityId}/bookings/new`);
  }

  const isLoading = communityQuery.isLoading || membersQuery.isLoading || myProfileQuery.isLoading;

  if (isInvalidCommunityId) {
    return (
      <ThemedView style={styles.container} background="background">
        <ScreenHeader onBack={() => router.back()} />
        <EmptyState message="잘못된 접근이에요" description="커뮤니티 링크를 다시 확인해 주세요" />
      </ThemedView>
    );
  }

  return (
    <ThemedView style={styles.container} background="background">
      <ScreenHeader title={communityQuery.data?.name} onBack={() => router.back()} />
      {isLoading && <LoadingView variant="skeleton" skeletonCount={4} />}

      {!isLoading && communityQuery.isError && (
        <ErrorView
          message="커뮤니티 정보를 불러오지 못했어요"
          onRetry={() => communityQuery.refetch()}
        />
      )}

      {!isLoading && !communityQuery.isError && myProfileQuery.isError && (
        <ErrorView message="내 정보를 불러오지 못했어요" onRetry={() => myProfileQuery.refetch()} />
      )}

      {!isLoading &&
        !communityQuery.isError &&
        !myProfileQuery.isError &&
        communityQuery.data === undefined && <EmptyState message="커뮤니티를 찾을 수 없어요" />}

      {!isLoading &&
        !communityQuery.isError &&
        !myProfileQuery.isError &&
        communityQuery.data !== undefined && (
          <>
            <ThemedView style={styles.tabsWrapper} background="background">
              <SegmentedControl options={tabOptions} value={activeTab} onChange={handleTabChange} />
            </ThemedView>

            <ScrollView contentContainerStyle={styles.content}>
              {activeTab === 'intro' && (
                <>
                  <CommunitySummary
                    community={communityQuery.data}
                    viewer={viewer}
                    onJoin={handleJoin}
                    onEnterChat={handleEnterChat}
                    isJoinPending={joinMutation.isPending}
                  />

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
                </>
              )}

              {activeTab === 'members' && (
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
              )}

              {activeTab === 'board' && isPostEnabled && (
                <CommunityBoardSection
                  communityId={communityId}
                  canWrite={canWritePost}
                  onCreatePost={handleCreatePost}
                  onPostPress={handlePostPress}
                />
              )}

              {activeTab === 'booking' && isBookingEnabled && (
                <CommunityBookingSection
                  communityId={communityId}
                  canLink={canLinkBooking}
                  onLinkPress={handleLinkBooking}
                />
              )}
            </ScrollView>
          </>
        )}
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  tabsWrapper: {
    paddingHorizontal: 16,
    paddingTop: 16,
  },
  content: {
    padding: 16,
    paddingBottom: 40,
  },
  membersSection: {
    marginTop: 0,
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
