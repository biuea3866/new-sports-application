/**
 * user-role-format — 마이페이지가 사용자 역할을 표시할 때 쓰는 순수 포맷 유틸.
 *
 * BE `domain/common/UserRoleName.kt`의 enum 값을 사람이 읽는 한글 라벨로 옮긴다.
 * 화면에 `USER` 같은 영문 enum 원문을 그대로 노출하지 않기 위한 매핑이다.
 */
const USER_ROLE_LABEL: Record<string, string> = {
  USER: '일반 회원',
  ADMIN: '관리자',
  FACILITY_OWNER: '시설 운영자',
  EVENT_HOST: '이벤트 주최자',
  GOODS_SELLER: '굿즈 판매자',
  OPERATIONS_MANAGER: '운영 담당자',
};

/** 역할이 하나도 없을 때의 기본 표시 — 인증된 사용자는 최소 일반 회원이다. */
const DEFAULT_ROLE_NAME = 'USER';

/**
 * 역할 목록을 한글 라벨로 이어 붙인다.
 * 매핑에 없는 값(BE에 새 역할이 추가된 경우)은 원문을 그대로 두어 정보를 잃지 않는다.
 */
export function formatUserRoleLabels(roles: string[] | undefined): string {
  const resolvedRoles = roles !== undefined && roles.length > 0 ? roles : [DEFAULT_ROLE_NAME];
  return resolvedRoles.map((role) => USER_ROLE_LABEL[role] ?? role).join(', ');
}
