/**
 * limitedDropDetailPresentation — S1 상세 화면의 순수 표시 로직.
 *
 * 근거: design-fe-app.md "텍스트 와이어프레임 S1" · "화면별 상태 표"
 * status별 CTA 라벨/비활성 여부, 에러 메시지 매핑을 컴포넌트 밖 순수 함수로 분리한다
 * (no-logic-in-component — 컴포넌트는 렌더링에만 집중).
 */
import { AxiosError } from 'axios';

import type { LimitedDropStatus } from '../api/types';

export interface DetailCtaConfig {
  label: string;
  disabled: boolean;
}

const CTA_CONFIG_BY_STATUS: Record<LimitedDropStatus, DetailCtaConfig> = {
  SCHEDULED: { label: '판매 시작 전', disabled: true },
  OPEN: { label: '구매하기', disabled: false },
  SOLD_OUT: { label: '재고 소진', disabled: true },
  CLOSED: { label: '판매 종료', disabled: true },
};

/**
 * 로컬 카운트다운이 openAt에 도달(isOpen)했으면, 서버 재동기화(refetch) 응답이 오기 전이라도
 * SCHEDULED를 OPEN처럼 취급해 CTA를 즉시 활성화한다.
 * 근거: design-fe-app.md "실패 경로·엣지" — "openAt 도달 시 CTA 활성화 + 1회 refetch로 동기화".
 */
export function resolveEffectiveStatus(
  status: LimitedDropStatus,
  isOpen: boolean
): LimitedDropStatus {
  if (status === 'SCHEDULED' && isOpen) {
    return 'OPEN';
  }
  return status;
}

export function getCtaConfig(status: LimitedDropStatus): DetailCtaConfig {
  return CTA_CONFIG_BY_STATUS[status];
}

/**
 * 404(존재하지 않는 회차)와 그 외 오류(5xx·네트워크)를 구분해 사용자 메시지를 반환한다.
 */
export function getDetailErrorMessage(error: unknown): string {
  if (error instanceof AxiosError && error.response?.status === 404) {
    return '존재하지 않는 회차예요';
  }
  return '불러오지 못했어요';
}
