/**
 * http-error — axios 에러 status 판별 순수 유틸 검증.
 */
import { AxiosError } from 'axios';
import { isForbiddenError, isNotFoundError } from '../http-error';

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
