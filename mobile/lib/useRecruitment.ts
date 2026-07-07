/**
 * useRecruitments/useRecruitment — GET /recruitments[?communityId=] TanStack Query 훅
 * useCreateRecruitment — POST /recruitments mutation 훅
 * useApplications — GET /recruitments/{id}/applications TanStack Query 훅(개설자 뷰)
 * useApplyRecruitment — POST /recruitments/{id}/applications mutation 훅
 * useMyApplications — GET /applications TanStack Query 훅(신청자 본인 뷰)
 * useCancelApplication — POST /applications/{id}/cancel mutation 훅
 * useCancelRecruitment — POST /recruitments/{id}/cancel mutation 훅
 *
 * 서버 상태(모집·신청)는 Query 캐시가 SSOT — 스토어에 복사하지 않는다.
 * 신청/취소는 결제·환불이 얽혀 있어 낙관적 업데이트를 적용하지 않는다(design-fe-app
 * "상태관리 설계") — 서버 확정 후 invalidate.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  applyRecruitment,
  cancelApplication,
  cancelRecruitment,
  createRecruitment,
  getRecruitment,
  listApplications,
  listMyApplications,
  listRecruitments,
} from '../api/recruitment';
import type {
  ApplicationResponse,
  ApplyRecruitmentRequest,
  ApplyRecruitmentResult,
  CreateRecruitmentRequest,
  RecruitmentResponse,
} from '../api/recruitment';

export const RECRUITMENTS_QUERY_KEY = ['recruitments'] as const;
export const MY_APPLICATIONS_QUERY_KEY = ['applications', 'me'] as const;

export function recruitmentsQueryKey(communityId?: number) {
  return [...RECRUITMENTS_QUERY_KEY, communityId ?? null] as const;
}

export function recruitmentQueryKey(id: number) {
  return ['recruitments', id] as const;
}

export function applicationsQueryKey(recruitmentId: number) {
  return ['recruitments', recruitmentId, 'applications'] as const;
}

/** `GET /recruitments?communityId=` — communityId 생략 시 전체 목록. */
export function useRecruitments(communityId?: number) {
  return useQuery<RecruitmentResponse[], Error>({
    queryKey: recruitmentsQueryKey(communityId),
    queryFn: () => listRecruitments(communityId),
  });
}

/** `GET /recruitments/{id}` */
export function useRecruitment(id: number) {
  return useQuery<RecruitmentResponse, Error>({
    queryKey: recruitmentQueryKey(id),
    queryFn: () => getRecruitment(id),
    enabled: id > 0,
  });
}

/** `POST /recruitments` — 개설 성공 시 목록 캐시를 무효화한다. */
export function useCreateRecruitment() {
  const queryClient = useQueryClient();

  return useMutation<RecruitmentResponse, Error, CreateRecruitmentRequest>({
    mutationFn: (body) => createRecruitment(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: RECRUITMENTS_QUERY_KEY });
    },
  });
}

/** `GET /recruitments/{id}/applications` — 개설자 전용 신청자 목록. */
export function useApplications(recruitmentId: number) {
  return useQuery<ApplicationResponse[], Error>({
    queryKey: applicationsQueryKey(recruitmentId),
    queryFn: () => listApplications(recruitmentId),
    enabled: recruitmentId > 0,
  });
}

/**
 * `POST /recruitments/{id}/applications` — 신청+결제 개시. 성공 시 모집 상세(정원 반영)와
 * 내 신청 목록 캐시를 무효화한다.
 */
export function useApplyRecruitment(recruitmentId: number) {
  const queryClient = useQueryClient();

  return useMutation<ApplyRecruitmentResult, Error, ApplyRecruitmentRequest>({
    mutationFn: (body) => applyRecruitment(recruitmentId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: recruitmentQueryKey(recruitmentId) });
      void queryClient.invalidateQueries({ queryKey: MY_APPLICATIONS_QUERY_KEY });
    },
  });
}

/** `GET /applications` — 신청자 본인 관점 전체 신청 목록. */
export function useMyApplications() {
  return useQuery<ApplicationResponse[], Error>({
    queryKey: MY_APPLICATIONS_QUERY_KEY,
    queryFn: () => listMyApplications(),
  });
}

/** `POST /applications/{id}/cancel` — 성공 시 내 신청 목록 캐시를 무효화한다. */
export function useCancelApplication() {
  const queryClient = useQueryClient();

  return useMutation<ApplicationResponse, Error, number>({
    mutationFn: (applicationId) => cancelApplication(applicationId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: MY_APPLICATIONS_QUERY_KEY });
    },
  });
}

/** `POST /recruitments/{id}/cancel` — 개설자 전용. 성공 시 상세·목록 캐시를 무효화한다. */
export function useCancelRecruitment() {
  const queryClient = useQueryClient();

  return useMutation<RecruitmentResponse, Error, number>({
    mutationFn: (recruitmentId) => cancelRecruitment(recruitmentId),
    onSuccess: (data) => {
      void queryClient.invalidateQueries({ queryKey: recruitmentQueryKey(data.id) });
      void queryClient.invalidateQueries({ queryKey: RECRUITMENTS_QUERY_KEY });
    },
  });
}
