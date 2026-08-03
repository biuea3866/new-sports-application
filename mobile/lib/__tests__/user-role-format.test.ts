/**
 * user-role-format — 역할 한글 라벨 매핑 검증.
 */
import { formatUserRoleLabels } from '../user-role-format';

describe('formatUserRoleLabels', () => {
  it('일반 회원 역할을 한글로 표기한다', () => {
    expect(formatUserRoleLabels(['USER'])).toBe('일반 회원');
  });

  it('여러 역할을 쉼표로 이어 표기한다', () => {
    expect(formatUserRoleLabels(['USER', 'FACILITY_OWNER'])).toBe('일반 회원, 시설 운영자');
  });

  it('역할 정보가 없으면 일반 회원으로 표기한다', () => {
    expect(formatUserRoleLabels(undefined)).toBe('일반 회원');
    expect(formatUserRoleLabels([])).toBe('일반 회원');
  });

  it('매핑에 없는 역할은 원문을 그대로 남겨 정보를 잃지 않는다', () => {
    expect(formatUserRoleLabels(['NEW_ROLE'])).toBe('NEW_ROLE');
  });

  it('영문 enum 원문을 그대로 노출하지 않는다', () => {
    ['USER', 'ADMIN', 'FACILITY_OWNER', 'EVENT_HOST', 'GOODS_SELLER', 'OPERATIONS_MANAGER'].forEach(
      (role) => {
        expect(formatUserRoleLabels([role])).not.toBe(role);
      }
    );
  });
});
