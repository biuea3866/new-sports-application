/**
 * limitedDropDetailPresentation — S1 상세 화면 CTA·에러 메시지 순수 로직 검증.
 */
import { AxiosError } from 'axios';

import {
  getCtaConfig,
  getDetailErrorMessage,
  resolveEffectiveStatus,
} from '../limitedDropDetailPresentation';

describe('getCtaConfig', () => {
  it('SCHEDULED는 비활성 "판매 시작 전"을 반환한다', () => {
    expect(getCtaConfig('SCHEDULED')).toEqual({ label: '판매 시작 전', disabled: true });
  });

  it('OPEN은 활성 "구매하기"를 반환한다', () => {
    expect(getCtaConfig('OPEN')).toEqual({ label: '구매하기', disabled: false });
  });

  it('SOLD_OUT은 비활성 "재고 소진"을 반환한다', () => {
    expect(getCtaConfig('SOLD_OUT')).toEqual({ label: '재고 소진', disabled: true });
  });

  it('CLOSED는 비활성 "판매 종료"를 반환한다', () => {
    expect(getCtaConfig('CLOSED')).toEqual({ label: '판매 종료', disabled: true });
  });
});

describe('resolveEffectiveStatus', () => {
  it('SCHEDULED이고 openAt에 아직 도달하지 않았으면 SCHEDULED를 유지한다', () => {
    expect(resolveEffectiveStatus('SCHEDULED', false)).toBe('SCHEDULED');
  });

  it('SCHEDULED이고 openAt에 도달했으면 OPEN으로 전환한다', () => {
    expect(resolveEffectiveStatus('SCHEDULED', true)).toBe('OPEN');
  });

  it('SCHEDULED가 아니면 isOpen 값과 무관하게 원래 status를 유지한다', () => {
    expect(resolveEffectiveStatus('SOLD_OUT', true)).toBe('SOLD_OUT');
    expect(resolveEffectiveStatus('CLOSED', true)).toBe('CLOSED');
  });
});

describe('getDetailErrorMessage', () => {
  it('404 응답이면 "존재하지 않는 회차예요"를 반환한다', () => {
    const notFoundError = new AxiosError('Not Found', undefined, undefined, undefined, {
      status: 404,
      data: {},
      statusText: 'Not Found',
      headers: {},
      config: {} as never,
    });

    expect(getDetailErrorMessage(notFoundError)).toBe('존재하지 않는 회차예요');
  });

  it('5xx 응답이면 "불러오지 못했어요"를 반환한다', () => {
    const serverError = new AxiosError('Internal Server Error', undefined, undefined, undefined, {
      status: 500,
      data: {},
      statusText: 'Internal Server Error',
      headers: {},
      config: {} as never,
    });

    expect(getDetailErrorMessage(serverError)).toBe('불러오지 못했어요');
  });

  it('네트워크 오류 등 일반 Error면 "불러오지 못했어요"를 반환한다', () => {
    expect(getDetailErrorMessage(new Error('Network Error'))).toBe('불러오지 못했어요');
  });
});
