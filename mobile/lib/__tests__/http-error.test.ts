/**
 * http-error — axios 에러 status 판별 순수 유틸 검증.
 */
import { AxiosError } from 'axios';
import {
  extractProblemCode,
  hasProblemCode,
  isForbiddenError,
  isNotFoundError,
  isUnauthorizedError,
} from '../http-error';

function axiosErrorWithStatus(status: number): AxiosError {
  return new AxiosError('boom', undefined, undefined, undefined, {
    status,
    data: {},
    statusText: '',
    headers: {},
    config: {} as never,
  });
}

describe('isForbiddenError', () => {
  it('403 axios 에러면 true를 반환한다', () => {
    expect(isForbiddenError(axiosErrorWithStatus(403))).toBe(true);
  });

  it('403이 아닌 axios 에러면 false를 반환한다', () => {
    expect(isForbiddenError(axiosErrorWithStatus(500))).toBe(false);
  });

  it('axios 에러가 아니면 false를 반환한다', () => {
    expect(isForbiddenError(new Error('boom'))).toBe(false);
  });

  it('에러가 없으면 false를 반환한다', () => {
    expect(isForbiddenError(null)).toBe(false);
  });
});

describe('isNotFoundError', () => {
  it('404 axios 에러면 true를 반환한다', () => {
    expect(isNotFoundError(axiosErrorWithStatus(404))).toBe(true);
  });

  it('404가 아니면 false를 반환한다', () => {
    expect(isNotFoundError(axiosErrorWithStatus(403))).toBe(false);
  });
});

describe('isUnauthorizedError', () => {
  it('401 axios 에러면 true를 반환한다', () => {
    expect(isUnauthorizedError(axiosErrorWithStatus(401))).toBe(true);
  });

  it('401이 아니면 false를 반환한다', () => {
    expect(isUnauthorizedError(axiosErrorWithStatus(500))).toBe(false);
  });

  it('axios 에러가 아니면 false를 반환한다', () => {
    expect(isUnauthorizedError(new Error('boom'))).toBe(false);
  });

  it('에러가 없으면 false를 반환한다', () => {
    expect(isUnauthorizedError(null)).toBe(false);
  });
});

/**
 * extractProblemCode — BE ProblemDetail 의 에러 코드 판별.
 * BE(ProblemDetailBuilder)는 `setProperty("code", ...)` 로 내려주고 `spring.mvc.problemdetails.enabled`
 * 미설정이라 unwrap 되지 않아 실제 직렬화는 `properties.code` 다 (BE 통합 테스트가 `$.properties.code` 로 검증).
 * 화면이 `errorCode` 같은 없는 필드를 읽어 안내가 죽는 사고를 막기 위한 단일 판별 지점이다.
 */
describe('extractProblemCode', () => {
  function problemDetailError(status: number, data: unknown): AxiosError {
    return new AxiosError('boom', undefined, undefined, undefined, {
      status,
      data,
      statusText: '',
      headers: {},
      config: {} as never,
    });
  }

  it('properties.code 에 중첩된 코드를 읽는다', () => {
    expect(extractProblemCode(problemDetailError(400, { properties: { code: 'INVALID_NICKNAME' } }))).toBe(
      'INVALID_NICKNAME'
    );
  });

  it('최상위 code 형태(구 응답)도 폴백으로 읽는다', () => {
    expect(extractProblemCode(problemDetailError(400, { code: 'INVALID_NICKNAME' }))).toBe(
      'INVALID_NICKNAME'
    );
  });

  it('코드가 없으면 undefined 를 반환한다', () => {
    expect(extractProblemCode(problemDetailError(400, { detail: '설명만 있음' }))).toBeUndefined();
    expect(extractProblemCode(problemDetailError(500, undefined))).toBeUndefined();
    expect(extractProblemCode(new Error('axios 에러가 아님'))).toBeUndefined();
    expect(extractProblemCode(null)).toBeUndefined();
  });

  it('errorCode 같은 존재하지 않는 필드는 읽지 않는다', () => {
    expect(extractProblemCode(problemDetailError(400, { errorCode: 'INVALID_NICKNAME' }))).toBeUndefined();
  });
});

describe('hasProblemCode', () => {
  function problemDetailError(status: number, code: string): AxiosError {
    return new AxiosError('boom', undefined, undefined, undefined, {
      status,
      data: { properties: { code } },
      statusText: '',
      headers: {},
      config: {} as never,
    });
  }

  it('status 와 code 가 모두 일치하면 true 를 반환한다', () => {
    expect(hasProblemCode(problemDetailError(400, 'INVALID_NICKNAME'), 400, 'INVALID_NICKNAME')).toBe(true);
  });

  it('code 가 다르면 false 를 반환한다', () => {
    expect(hasProblemCode(problemDetailError(400, 'INVALID_EMAIL'), 400, 'INVALID_NICKNAME')).toBe(false);
  });

  it('status 가 다르면 false 를 반환한다', () => {
    expect(hasProblemCode(problemDetailError(409, 'INVALID_NICKNAME'), 400, 'INVALID_NICKNAME')).toBe(false);
  });
});
