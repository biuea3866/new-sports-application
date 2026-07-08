/**
 * community-form — 커뮤니티 개설 폼 유효성 검증·요청 변환 순수 로직.
 *
 * 근거: FE-11 티켓 "개설 폼 필수값 미입력 시 CTA가 비활성이다", design-fe-app.md S4
 * ("이름·설명·종목·공개여부 폼, 하단 고정 단일 CTA(유효 시 활성)").
 * 화면 컴포넌트는 렌더링에만 집중하도록 검증·변환 로직을 이 유틸로 분리한다.
 */
import type {
  CommunityVisibility,
  CreateCommunityRequest,
  SportCategory,
} from '../api/community-types';

export interface CreateCommunityFormValues {
  name: string;
  description: string;
  sportCategory: SportCategory | null;
  visibility: CommunityVisibility;
}

/** 이름·종목이 모두 채워져야 개설 CTA가 활성화된다. */
export function isCreateCommunityFormValid(values: CreateCommunityFormValues): boolean {
  return values.name.trim().length > 0 && values.sportCategory !== null;
}

/**
 * 유효성 검증을 통과한 폼 값을 `POST /communities` 요청 본문으로 변환한다.
 * `sportCategory`가 없는 상태로 호출되면 방어적으로 예외를 던진다 —
 * 호출부는 `isCreateCommunityFormValid`로 먼저 검증한 뒤에만 이 함수를 호출해야 한다.
 */
export function toCreateCommunityRequest(
  values: CreateCommunityFormValues
): CreateCommunityRequest {
  const { sportCategory } = values;
  if (sportCategory === null) {
    throw new Error('sportCategory가 없는 상태로 개설 요청을 생성할 수 없습니다.');
  }

  const trimmedName = values.name.trim();
  const trimmedDescription = values.description.trim();

  return {
    name: trimmedName,
    description: trimmedDescription.length > 0 ? trimmedDescription : undefined,
    visibility: values.visibility,
    sportCategory,
  };
}
