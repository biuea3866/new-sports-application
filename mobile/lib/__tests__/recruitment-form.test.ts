/**
 * recruitment-form — 모집 개설 폼(A-R3) 유효성 검증·요청 변환 순수 로직 검증.
 * 근거: design-fe-app.md "화면별 4상태 표" A-R3("정원≥1·참가비≥0·마감<활동일 인라인 검증"),
 * Testing Plan "개설 폼"(정원<1·참가비<0 인라인 검증 / 마감<활동일 검증).
 */
import {
  isCreateRecruitmentFormValid,
  toCreateRecruitmentRequest,
  validateCreateRecruitmentForm,
  type CreateRecruitmentFormValues,
} from '../recruitment-form';

const VALID_VALUES: CreateRecruitmentFormValues = {
  title: '주말 축구 3명 모집',
  description: '한강공원에서 진행합니다',
  capacityText: '3',
  feeAmountText: '5000',
  activityAt: '2026-07-12T14:00:00+09:00',
  applicationDeadline: '2026-07-10T23:00:00+09:00',
  communityId: null,
};

describe('validateCreateRecruitmentForm', () => {
  it('유효한 값이면 오류가 없다', () => {
    expect(validateCreateRecruitmentForm(VALID_VALUES)).toEqual({});
  });

  it('정원이 1 미만이면 오류를 반환한다', () => {
    const errors = validateCreateRecruitmentForm({ ...VALID_VALUES, capacityText: '0' });
    expect(errors.capacity).toBeDefined();
  });

  it('참가비가 음수면 오류를 반환한다', () => {
    const errors = validateCreateRecruitmentForm({ ...VALID_VALUES, feeAmountText: '-1' });
    expect(errors.feeAmount).toBeDefined();
  });

  it('신청마감이 활동일보다 늦으면 오류를 반환한다', () => {
    const errors = validateCreateRecruitmentForm({
      ...VALID_VALUES,
      activityAt: '2026-07-10T00:00:00+09:00',
      applicationDeadline: '2026-07-12T00:00:00+09:00',
    });
    expect(errors.deadline).toBeDefined();
  });
});

describe('isCreateRecruitmentFormValid', () => {
  it('제목이 비어 있으면 유효하지 않다', () => {
    expect(isCreateRecruitmentFormValid({ ...VALID_VALUES, title: '   ' })).toBe(false);
  });

  it('모든 값이 유효하면 true를 반환한다', () => {
    expect(isCreateRecruitmentFormValid(VALID_VALUES)).toBe(true);
  });

  it('정원이 정수가 아니면 유효하지 않다', () => {
    expect(isCreateRecruitmentFormValid({ ...VALID_VALUES, capacityText: '3.5' })).toBe(false);
  });
});

describe('toCreateRecruitmentRequest', () => {
  it('폼 값을 요청 본문으로 변환한다', () => {
    const request = toCreateRecruitmentRequest(VALID_VALUES);

    expect(request).toEqual({
      title: '주말 축구 3명 모집',
      description: '한강공원에서 진행합니다',
      capacity: 3,
      feeAmount: 5000,
      activityAt: new Date('2026-07-12T14:00:00+09:00').toISOString(),
      applicationDeadline: new Date('2026-07-10T23:00:00+09:00').toISOString(),
      communityId: undefined,
    });
  });

  it('설명이 빈 문자열이면 description을 생략한다', () => {
    const request = toCreateRecruitmentRequest({ ...VALID_VALUES, description: '   ' });
    expect(request.description).toBeUndefined();
  });

  it('communityId가 지정되면 그대로 전달한다', () => {
    const request = toCreateRecruitmentRequest({ ...VALID_VALUES, communityId: 7 });
    expect(request.communityId).toBe(7);
  });
});
