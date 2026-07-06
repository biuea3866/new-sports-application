/**
 * community-form — 개설 폼 유효성·요청 변환 순수 로직 검증.
 * 근거: FE-11 티켓 "개설 폼 필수값 미입력 시 CTA가 비활성이다".
 */
import {
  isCreateCommunityFormValid,
  toCreateCommunityRequest,
  type CreateCommunityFormValues,
} from '../community-form';

const VALID_VALUES: CreateCommunityFormValues = {
  name: '주말 축구 모임',
  description: '동네 축구 같이 해요',
  sportCategory: 'SOCCER',
  visibility: 'PUBLIC',
};

describe('isCreateCommunityFormValid', () => {
  it('이름과 종목이 모두 채워지면 유효하다', () => {
    expect(isCreateCommunityFormValid(VALID_VALUES)).toBe(true);
  });

  it('이름이 공백뿐이면 유효하지 않다', () => {
    expect(isCreateCommunityFormValid({ ...VALID_VALUES, name: '   ' })).toBe(false);
  });

  it('종목을 선택하지 않으면 유효하지 않다', () => {
    expect(isCreateCommunityFormValid({ ...VALID_VALUES, sportCategory: null })).toBe(false);
  });
});

describe('toCreateCommunityRequest', () => {
  it('앞뒤 공백을 제거해 요청 본문으로 변환한다', () => {
    const request = toCreateCommunityRequest({
      ...VALID_VALUES,
      name: '  주말 축구 모임  ',
      description: '  동네 축구 같이 해요  ',
    });

    expect(request).toEqual({
      name: '주말 축구 모임',
      description: '동네 축구 같이 해요',
      visibility: 'PUBLIC',
      sportCategory: 'SOCCER',
    });
  });

  it('설명이 빈 문자열이면 description을 생략한다', () => {
    const request = toCreateCommunityRequest({ ...VALID_VALUES, description: '   ' });

    expect(request.description).toBeUndefined();
  });

  it('sportCategory가 없으면 예외를 던진다(방어적 — 유효성 검증 통과 후 호출 전제)', () => {
    expect(() => toCreateCommunityRequest({ ...VALID_VALUES, sportCategory: null })).toThrow();
  });
});
