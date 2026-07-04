/**
 * community.ts — 커뮤니티(동아리) REST API 함수
 *
 * 근거: `20260704-채팅시스템고도화-tdd.md` "REST API 계약", design-fe-app "API 연동 표"(S3~S5).
 * 모두 `getBeClient()` 경유 — 컴포넌트에서 직접 호출 금지, `lib/useCommunity.ts` 훅을 통해서만 사용한다.
 */
import { getBeClient } from './be-client';
import type {
  CommunityMemberResponse,
  CommunityResponse,
  CreateCommunityRequest,
  MembershipResponse,
  TransferHostRequest,
} from './community-types';

/**
 * `GET /communities?keyword=` — BE 확정 대기(역제안, design-fe-app "API 연동 표" S3).
 * 공개 커뮤니티 목록·키워드 검색. 인가 불요.
 */
export async function listCommunities(keyword?: string): Promise<CommunityResponse[]> {
  const res = await getBeClient().get<CommunityResponse[]>('/communities', {
    params: keyword ? { keyword } : undefined,
  });
  return res.data;
}

/**
 * `GET /communities/{id}` — BE 확정 대기(역제안, design-fe-app "API 연동 표" S5).
 * 공개 커뮤니티는 누구나, 비공개는 ACTIVE 멤버만 조회 가능(서버 인가).
 */
export async function getCommunity(id: number): Promise<CommunityResponse> {
  const res = await getBeClient().get<CommunityResponse>(`/communities/${id}`);
  return res.data;
}

/**
 * `GET /communities/{id}/members` — BE 확정 대기(역제안, design-fe-app "API 연동 표" S5).
 * ACTIVE 멤버 목록만 반환(FR-13 ② 멤버십 범위 인가).
 */
export async function listMembers(id: number): Promise<CommunityMemberResponse[]> {
  const res = await getBeClient().get<CommunityMemberResponse[]>(`/communities/${id}/members`);
  return res.data;
}

/** `POST /communities` — 커뮤니티 개설(성공 시 전용 그룹 방 자동 생성, FR-1). */
export async function createCommunity(req: CreateCommunityRequest): Promise<CommunityResponse> {
  const res = await getBeClient().post<CommunityResponse>('/communities', req);
  return res.data;
}

/** `POST /communities/{id}/join` — 공개는 즉시 ACTIVE, 비공개는 PENDING_APPROVAL(FR-2). */
export async function joinCommunity(id: number): Promise<MembershipResponse> {
  const res = await getBeClient().post<MembershipResponse>(`/communities/${id}/join`);
  return res.data;
}

/** `POST /communities/{id}/members/{userId}/approve` — 방장만 가능(FR-2). */
export async function approveMember(id: number, userId: number): Promise<CommunityMemberResponse> {
  const res = await getBeClient().post<CommunityMemberResponse>(
    `/communities/${id}/members/${userId}/approve`
  );
  return res.data;
}

/** `POST /communities/{id}/members/{userId}/kick` — 방장만 가능, 204(FR-3/5). */
export async function kickMember(id: number, userId: number): Promise<void> {
  await getBeClient().post(`/communities/${id}/members/${userId}/kick`);
}

/** `POST /communities/{id}/host/transfer` — 방장 위임, 200(FR-3). */
export async function transferHost(id: number, newHostUserId: number): Promise<void> {
  const body: TransferHostRequest = { newHostUserId };
  await getBeClient().post(`/communities/${id}/host/transfer`, body);
}

/** `DELETE /communities/{id}/members/me` — 탈퇴, 204(FR-5). */
export async function leaveCommunity(id: number): Promise<void> {
  await getBeClient().delete(`/communities/${id}/members/me`);
}
