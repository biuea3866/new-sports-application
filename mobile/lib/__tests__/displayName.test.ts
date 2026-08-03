/**
 * resolveDisplayName — 사용자 표시 이름 폴백.
 * 갤러리 결함(작성자 `사용자 71`·`방장 #68`·`초대자 #71`)의 재발을 막는 단일 지점이다:
 * 이름이 없어도 내부 식별자·이메일을 노출하지 않는다.
 */
import { resolveDisplayName, UNSET_NICKNAME_DISPLAY_NAME } from '../displayName';

describe('resolveDisplayName', () => {
  it('닉네임이 있으면 그대로 보여준다', () => {
    expect(resolveDisplayName('김철수')).toBe('김철수');
  });

  it('닉네임이 없으면 중립 기본값을 보여준다', () => {
    expect(resolveDisplayName(undefined)).toBe(UNSET_NICKNAME_DISPLAY_NAME);
    expect(resolveDisplayName(null)).toBe(UNSET_NICKNAME_DISPLAY_NAME);
  });

  it('공백뿐인 닉네임도 기본값으로 대체한다', () => {
    expect(resolveDisplayName('   ')).toBe(UNSET_NICKNAME_DISPLAY_NAME);
  });

  it('기본값에는 이메일·내부 식별자가 들어가지 않는다', () => {
    expect(UNSET_NICKNAME_DISPLAY_NAME).not.toMatch(/@|#|\d/);
  });
});
