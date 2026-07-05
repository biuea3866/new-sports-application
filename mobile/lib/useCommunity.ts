/**
 * useCommunities/useCommunity/useCommunityMembers — 커뮤니티 조회 TanStack Query 훅
 * useCreateCommunity/useJoinCommunity/useApproveMember/useKickMember/useTransferHost/useLeaveCommunity
 * — 커뮤니티 멤버십·역할 mutation 훅
 *
 * 근거: `20260704-채팅시스템고도화-design-fe-app.md` "API 연동 표"(S3~S5).
 * 서버 상태(커뮤니티·멤버십)는 Query 캐시가 SSOT — 스토어에 복사하지 않는다.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  approveMember,
  createCommunity,
  getCommunity,
  joinCommunity,
  kickMember,
  leaveCommunity,
  listCommunities,
  listMembers,
  transferHost,
} from '../api/community';
import type {
  CommunityMemberResponse,
  CommunityResponse,
  CreateCommunityRequest,
  MembershipResponse,
} from '../api/community-types';
import { MY_ROOMS_QUERY_KEY } from './useRooms';

export const COMMUNITIES_QUERY_KEY = ['communities'] as const;

export function communitiesQueryKey(keyword?: string) {
  return [...COMMUNITIES_QUERY_KEY, keyword ?? null] as const;
}

export function communityQueryKey(id: number) {
  return ['communities', id] as const;
}

export function communityMembersQueryKey(id: number) {
  return ['communities', id, 'members'] as const;
}

/** `GET /communities?keyword=` 목록·키워드 검색 훅. */
export function useCommunities(keyword?: string) {
  return useQuery<CommunityResponse[], Error>({
    queryKey: communitiesQueryKey(keyword),
    queryFn: () => listCommunities(keyword),
  });
}

/** `GET /communities/{id}` 상세 훅. */
export function useCommunity(id: number) {
  return useQuery<CommunityResponse, Error>({
    queryKey: communityQueryKey(id),
    queryFn: () => getCommunity(id),
    enabled: id > 0,
  });
}

/** `GET /communities/{id}/members` ACTIVE 멤버 목록 훅. */
export function useCommunityMembers(id: number) {
  return useQuery<CommunityMemberResponse[], Error>({
    queryKey: communityMembersQueryKey(id),
    queryFn: () => listMembers(id),
    enabled: id > 0,
  });
}

/**
 * `POST /communities` — 개설 성공 시 방목록(`rooms/me`)·커뮤니티목록 캐시를 무효화한다.
 * 전용 그룹 방이 서버에서 자동 생성되므로 방목록도 함께 갱신해야 한다.
 */
export function useCreateCommunity() {
  const queryClient = useQueryClient();

  return useMutation<CommunityResponse, Error, CreateCommunityRequest>({
    mutationFn: (req) => createCommunity(req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: MY_ROOMS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: COMMUNITIES_QUERY_KEY });
    },
  });
}

/** `POST /communities/{id}/join` — 공개=ACTIVE 즉시, 비공개=PENDING_APPROVAL. */
export function useJoinCommunity(communityId: number) {
  const queryClient = useQueryClient();

  return useMutation<MembershipResponse, Error, void>({
    mutationFn: () => joinCommunity(communityId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: communityMembersQueryKey(communityId) });
    },
  });
}

interface ApproveMemberVariables {
  userId: number;
}

/** `POST /communities/{id}/members/{userId}/approve` — 방장만 가능. */
export function useApproveMember(communityId: number) {
  const queryClient = useQueryClient();

  return useMutation<CommunityMemberResponse, Error, ApproveMemberVariables>({
    mutationFn: ({ userId }) => approveMember(communityId, userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: communityMembersQueryKey(communityId) });
    },
  });
}

interface KickMemberVariables {
  userId: number;
}

/** `POST /communities/{id}/members/{userId}/kick` — 방장만 가능. */
export function useKickMember(communityId: number) {
  const queryClient = useQueryClient();

  return useMutation<void, Error, KickMemberVariables>({
    mutationFn: ({ userId }) => kickMember(communityId, userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: communityMembersQueryKey(communityId) });
    },
  });
}

interface TransferHostVariables {
  newHostUserId: number;
}

/** `POST /communities/{id}/host/transfer` — 방장 위임. */
export function useTransferHost(communityId: number) {
  const queryClient = useQueryClient();

  return useMutation<void, Error, TransferHostVariables>({
    mutationFn: ({ newHostUserId }) => transferHost(communityId, newHostUserId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: communityQueryKey(communityId) });
      void queryClient.invalidateQueries({ queryKey: communityMembersQueryKey(communityId) });
    },
  });
}

/**
 * `DELETE /communities/{id}/members/me` — 탈퇴 성공 시 방목록(`rooms/me`) 캐시를 무효화한다.
 * 전용 그룹 방에서도 자동 퇴장되므로 방목록을 함께 갱신해야 한다.
 */
export function useLeaveCommunity(communityId: number) {
  const queryClient = useQueryClient();

  return useMutation<void, Error, void>({
    mutationFn: () => leaveCommunity(communityId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: MY_ROOMS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: communityMembersQueryKey(communityId) });
    },
  });
}
