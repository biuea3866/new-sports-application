/**
 * nickname — 닉네임 관련 BE 계약 상수·문구.
 * 회원가입·마이페이지 두 화면이 같은 규칙 안내를 쓰도록 한 곳에 둔다.
 */

/** BE `InvalidNicknameException` 의 errorCode (ProblemDetail 확장 멤버 — 최상위 `code` 로 평탄화된다). */
export const INVALID_NICKNAME_CODE = 'INVALID_NICKNAME';

/** 규칙 위반 시 사용자에게 보여줄 안내. BE `User.changeNickname` 규칙과 같은 내용이다. */
export const NICKNAME_RULE_MESSAGE = '닉네임은 한글·영문·숫자·밑줄 2~20자로 입력해 주세요.';
