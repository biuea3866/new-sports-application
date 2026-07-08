/**
 * recruitment-form — 모집 개설 폼(A-R3) 유효성 검증·요청 변환 순수 로직.
 *
 * 근거: design-fe-app.md "화면별 4상태 표" A-R3, Testing Plan "개설 폼".
 * 화면 컴포넌트는 렌더링에만 집중하도록 검증·변환 로직을 이 유틸로 분리한다.
 *
 * 날짜/시각 입력은 네이티브 date-picker 라이브러리가 레포에 없어 ISO 형태 텍스트
 * 입력(`YYYY-MM-DDTHH:mm`)으로 받는다 — 신규 의존성 도입 없이 최소 구현한다.
 */
import type { CreateRecruitmentRequest } from '../api/recruitment';

export interface CreateRecruitmentFormValues {
  title: string;
  description: string;
  capacityText: string;
  feeAmountText: string;
  activityAt: string;
  applicationDeadline: string;
  communityId: number | null;
}

export interface CreateRecruitmentFormErrors {
  capacity?: string;
  feeAmount?: string;
  deadline?: string;
}

function parseDate(value: string): Date | null {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

/** 정원≥1(정수)·참가비≥0·마감<활동일을 검증해 필드별 오류 메시지를 반환한다. */
export function validateCreateRecruitmentForm(
  values: CreateRecruitmentFormValues
): CreateRecruitmentFormErrors {
  const errors: CreateRecruitmentFormErrors = {};

  const capacity = Number(values.capacityText);
  if (!Number.isInteger(capacity) || capacity < 1) {
    errors.capacity = '정원은 1명 이상의 정수로 입력해주세요';
  }

  const feeAmount = Number(values.feeAmountText);
  if (Number.isNaN(feeAmount) || feeAmount < 0) {
    errors.feeAmount = '참가비는 0원 이상으로 입력해주세요';
  }

  const activityDate = parseDate(values.activityAt);
  const deadlineDate = parseDate(values.applicationDeadline);
  if (activityDate !== null && deadlineDate !== null) {
    if (deadlineDate.getTime() >= activityDate.getTime()) {
      errors.deadline = '신청마감은 활동일보다 이전이어야 해요';
    }
  }

  return errors;
}

/** 제목이 채워지고 날짜가 유효 형식이며 검증 오류가 없어야 개설 CTA가 활성화된다. */
export function isCreateRecruitmentFormValid(values: CreateRecruitmentFormValues): boolean {
  if (values.title.trim().length === 0) {
    return false;
  }

  const activityDate = parseDate(values.activityAt);
  const deadlineDate = parseDate(values.applicationDeadline);
  if (activityDate === null || deadlineDate === null) {
    return false;
  }

  const errors = validateCreateRecruitmentForm(values);
  return Object.keys(errors).length === 0;
}

/**
 * 유효성 검증을 통과한 폼 값을 `POST /recruitments` 요청 본문으로 변환한다.
 * 호출부는 `isCreateRecruitmentFormValid`로 먼저 검증한 뒤에만 호출해야 한다.
 */
export function toCreateRecruitmentRequest(
  values: CreateRecruitmentFormValues
): CreateRecruitmentRequest {
  const activityDate = parseDate(values.activityAt);
  const deadlineDate = parseDate(values.applicationDeadline);
  if (activityDate === null || deadlineDate === null) {
    throw new Error('활동일·신청마감이 유효하지 않은 상태로 요청을 생성할 수 없습니다.');
  }

  const trimmedDescription = values.description.trim();

  return {
    title: values.title.trim(),
    description: trimmedDescription.length > 0 ? trimmedDescription : undefined,
    capacity: Number(values.capacityText),
    feeAmount: Number(values.feeAmountText),
    activityAt: activityDate.toISOString(),
    applicationDeadline: deadlineDate.toISOString(),
    communityId: values.communityId ?? undefined,
  };
}
