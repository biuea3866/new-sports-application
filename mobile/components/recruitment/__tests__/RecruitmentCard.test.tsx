/**
 * RecruitmentCard(A-R1) — 모집 목록 카드의 메타 라인·상태 배지 표시 검증.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import { RecruitmentCard } from '../RecruitmentCard';
import type { RecruitmentResponse } from '../../../api/recruitment';

const FIXED_NOW = new Date('2026-08-01T09:00:00+09:00');

function buildRecruitment(overrides: Partial<RecruitmentResponse> = {}): RecruitmentResponse {
  return {
    id: 1,
    title: '새벽 한강 10K 페이스 러닝',
    description: null,
    capacity: 15,
    feeAmount: 0,
    activityAt: '2026-08-10T21:00:00+09:00',
    applicationDeadline: '2026-08-03T23:59:00+09:00',
    communityId: null,
    recruiterUserId: 68,
    status: 'OPEN',
    ...overrides,
  };
}

describe('RecruitmentCard', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    jest.useFakeTimers().setSystemTime(FIXED_NOW);
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('마감 전 모집은 D-day와 마감을 한 번씩만 표기한다', () => {
    render(<RecruitmentCard recruitment={buildRecruitment()} onPress={jest.fn()} />);

    expect(screen.getByText('정원 15 · 무료 · D-2 마감')).toBeTruthy();
  });

  it('마감이 지난 모집은 "마감"을 중복 표기하지 않는다', () => {
    render(
      <RecruitmentCard
        recruitment={buildRecruitment({ applicationDeadline: '2026-07-20T23:59:00+09:00' })}
        onPress={jest.fn()}
      />
    );

    expect(screen.getByText('정원 15 · 무료 · 마감')).toBeTruthy();
    expect(screen.queryByText('정원 15 · 무료 · 마감 마감')).toBeNull();
  });

  it('모집 중 배지는 한글 라벨로 표시한다', () => {
    render(<RecruitmentCard recruitment={buildRecruitment()} onPress={jest.fn()} />);

    expect(screen.getByText('모집 중')).toBeTruthy();
    expect(screen.queryByText('OPEN')).toBeNull();
  });

  it('신청마감이 지난 모집에는 모집 중 배지를 달지 않는다', () => {
    render(
      <RecruitmentCard
        recruitment={buildRecruitment({ applicationDeadline: '2026-07-20T23:59:00+09:00' })}
        onPress={jest.fn()}
      />
    );

    expect(screen.getByText('마감됨')).toBeTruthy();
    expect(screen.queryByText('모집 중')).toBeNull();
  });

  it('취소된 모집은 취소됨 배지를 표시한다', () => {
    render(
      <RecruitmentCard
        recruitment={buildRecruitment({ status: 'CANCELLED' })}
        onPress={jest.fn()}
      />
    );

    expect(screen.getByText('취소됨')).toBeTruthy();
  });

  it('다크 모드에서도 상태 배지가 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<RecruitmentCard recruitment={buildRecruitment()} onPress={jest.fn()} />);

    expect(screen.getByText('모집 중')).toBeTruthy();
  });
});
