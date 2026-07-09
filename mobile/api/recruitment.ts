/**
 * recruitment.ts — 모집(recruitment) API 타입 및 호출
 *
 * 근거: `20260707-모집-시설상품-소모임예약연동-tdd.md` "REST API 계약", BE
 * `RecruitmentApiController`·`ApplicationApiController`(신청자 본인 관점,
 * `/applications` — `/recruitments/{id}/applications`(개설자 관점)와 별도 경로).
 * 컴포넌트에서 직접 호출 금지 — `lib/useRecruitment.ts` 훅을 통해서만 사용한다.
 */
import { getBeClient } from './be-client';
import type { PaymentMethod } from './payment';

/** BE `domain/recruitment/entity/RecruitmentStatus`. */
export type RecruitmentStatus = 'OPEN' | 'CLOSED' | 'CANCELLED';

/** BE `domain/recruitment/entity/ApplicationStatus`. */
export type ApplicationStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'REFUNDED';

/** `application/recruitment/dto/RecruitmentResponse` — Controller가 그대로 반환한다. */
export interface RecruitmentResponse {
  id: number;
  title: string;
  description: string | null;
  capacity: number;
  /** BigDecimal → JSON number(Jackson 기본 직렬화) */
  feeAmount: number;
  activityAt: string; // ISO-8601
  applicationDeadline: string; // ISO-8601
  communityId: number | null;
  recruiterUserId: number;
  status: RecruitmentStatus;
}

/** `POST /recruitments` 요청 본문 — `recruiterUserId`는 인증 principal에서 서버가 채운다. */
export interface CreateRecruitmentRequest {
  title: string;
  description?: string | null;
  capacity: number;
  feeAmount: number;
  activityAt: string;
  applicationDeadline: string;
  communityId?: number | null;
}

/** `application/recruitment/dto/ApplicationResponse` — 개설자·본인 신청 목록 공용. */
export interface ApplicationResponse {
  id: number;
  recruitmentId: number;
  status: ApplicationStatus;
  paymentId: number | null;
  appliedAt: string;
}

/**
 * `GET /applications/{id}` 응답 — 신청 상세(단건). 주문상세(Option A+) 화면 전용 계약.
 * BE `application/recruitment/dto/ApplicationDetailResponse.kt`를 그대로 반영한다(origin/main
 * 머지 완료). 필드명은 `id`가 아니라 `applicationId`로 명명된 별도 계약이다
 * (`ApplicationResponse`와 혼용하지 않는다). `feeAmount`는 BigDecimal이라 항상 non-null이다
 * (무료 모집이면 0).
 */
export interface ApplicationDetailResponse {
  applicationId: number;
  recruitmentId: number;
  recruitmentTitle: string;
  status: ApplicationStatus;
  feeAmount: number;
  paymentId: number | null;
  createdAt: string; // ISO-8601
}

/** `GET /applications/{id}` — 신청 상세(단건). 주문상세(Option A) 화면이 사용한다. */
export async function getApplicationDetail(id: number): Promise<ApplicationDetailResponse> {
  const res = await getBeClient().get<ApplicationDetailResponse>(`/applications/${id}`);
  return res.data;
}

/** `POST /recruitments/{id}/applications` 요청 본문. */
export interface ApplyRecruitmentRequest {
  paymentMethod: PaymentMethod;
  currency: string;
}

/**
 * `application/recruitment/dto/ApplyRecruitmentResult` — `POST /recruitments/{id}/applications`
 * 응답(202). `checkoutUrl`은 무료(`feeAmount=0`) 신청이면 null — 이 경우 결제 화면 진입 없이
 * 즉시 CONFIRMED 처리된다(design-fe-app "결제 흐름 재사용 결정").
 */
export interface ApplyRecruitmentResult {
  id: number;
  recruitmentId: number;
  status: ApplicationStatus;
  paymentId: number | null;
  checkoutUrl: string | null;
  appliedAt: string;
}

/** `GET /recruitments?communityId=` */
export async function listRecruitments(communityId?: number): Promise<RecruitmentResponse[]> {
  const res = await getBeClient().get<RecruitmentResponse[]>('/recruitments', {
    params: communityId !== undefined ? { communityId } : undefined,
  });
  return res.data;
}

/** `GET /recruitments/{id}` */
export async function getRecruitment(id: number): Promise<RecruitmentResponse> {
  const res = await getBeClient().get<RecruitmentResponse>(`/recruitments/${id}`);
  return res.data;
}

/** `POST /recruitments` */
export async function createRecruitment(
  body: CreateRecruitmentRequest
): Promise<RecruitmentResponse> {
  const res = await getBeClient().post<RecruitmentResponse>('/recruitments', body);
  return res.data;
}

/** `GET /recruitments/{id}/applications` — 개설자 전용(신청자 목록). */
export async function listApplications(recruitmentId: number): Promise<ApplicationResponse[]> {
  const res = await getBeClient().get<ApplicationResponse[]>(
    `/recruitments/${recruitmentId}/applications`
  );
  return res.data;
}

/** `POST /recruitments/{id}/applications` — 신청+결제 개시(202). */
export async function applyRecruitment(
  recruitmentId: number,
  body: ApplyRecruitmentRequest
): Promise<ApplyRecruitmentResult> {
  const res = await getBeClient().post<ApplyRecruitmentResult>(
    `/recruitments/${recruitmentId}/applications`,
    body
  );
  return res.data;
}

/** `POST /recruitments/{id}/cancel` — 개설자 전용(모집 취소). */
export async function cancelRecruitment(id: number): Promise<RecruitmentResponse> {
  const res = await getBeClient().post<RecruitmentResponse>(`/recruitments/${id}/cancel`);
  return res.data;
}

/** `GET /applications` — 신청자 본인 관점 전체 신청 목록(X-User-Id 기준). */
export async function listMyApplications(): Promise<ApplicationResponse[]> {
  const res = await getBeClient().get<ApplicationResponse[]>('/applications');
  return res.data;
}

/** `POST /applications/{id}/cancel` — 신청자 본인 취소(단계 수수료 적용). */
export async function cancelApplication(id: number): Promise<ApplicationResponse> {
  const res = await getBeClient().post<ApplicationResponse>(`/applications/${id}/cancel`);
  return res.data;
}
