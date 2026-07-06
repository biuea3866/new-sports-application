/**
 * CommunityNewScreen(S4, FE-11) — 개설 폼 유효성·제출 결과 검증.
 * 근거: FE-11 티켓 테스트 케이스, design-fe-app.md S4.
 *
 * useCreateCommunity를 모킹해 화면 배선(CTA 활성화·이동·실패 인라인)만 사용자 관점으로 검증한다.
 * 방목록 캐시 무효화는 useCreateCommunity(FE-07)의 책임이며 이 화면은 성공 콜백에서
 * 상세 화면으로 이동만 한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { CommunityResponse } from '../../../api/community-types';
import CommunityNewScreen from '../new';

jest.mock('../../../lib/useCommunity', () => ({
  useCreateCommunity: jest.fn(),
}));

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
}));

import { router } from 'expo-router';
import { useCreateCommunity } from '../../../lib/useCommunity';

const useCreateCommunityMock = useCreateCommunity as jest.MockedFunction<typeof useCreateCommunity>;

const CREATED_COMMUNITY: CommunityResponse = {
  id: 5,
  name: '주말 축구 모임',
  description: null,
  visibility: 'PUBLIC',
  sportCategory: 'SOCCER',
  hostUserId: 10,
  memberCount: 1,
  roomId: 200,
  createdAt: '2026-07-06T00:00:00+09:00',
};

function mockCreateCommunity(
  mutate: jest.Mock,
  overrides: Partial<ReturnType<typeof useCreateCommunity>> = {}
) {
  useCreateCommunityMock.mockReturnValue({
    mutate,
    mutateAsync: jest.fn(),
    isPending: false,
    ...overrides,
  } as unknown as ReturnType<typeof useCreateCommunity>);
}

describe('CommunityNewScreen', () => {
  let createCommunityMock: jest.Mock;

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    createCommunityMock = jest.fn();
    mockCreateCommunity(createCommunityMock);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('이름·종목을 입력하지 않으면 개설 CTA가 비활성이다', () => {
    render(<CommunityNewScreen />);

    expect(screen.getByLabelText('개설하기').props.accessibilityState.disabled).toBe(true);
  });

  it('이름과 종목을 채우면 개설 CTA가 활성화된다', () => {
    render(<CommunityNewScreen />);

    fireEvent.changeText(screen.getByLabelText('이름'), '주말 축구 모임');
    fireEvent.press(screen.getByLabelText('⚽ 축구'));

    expect(screen.getByLabelText('개설하기').props.accessibilityState.disabled).toBe(false);
  });

  it('개설 성공 시 상세 화면으로 이동한다', () => {
    createCommunityMock.mockImplementation((_request, options) => {
      options?.onSuccess?.(CREATED_COMMUNITY);
    });

    render(<CommunityNewScreen />);
    fireEvent.changeText(screen.getByLabelText('이름'), '주말 축구 모임');
    fireEvent.press(screen.getByLabelText('⚽ 축구'));
    fireEvent.press(screen.getByLabelText('개설하기'));

    expect(router.replace).toHaveBeenCalledWith('/communities/5');
  });

  it('개설 실패 시 인라인 오류가 표시되고 CTA가 복구된다', () => {
    createCommunityMock.mockImplementation((_request, options) => {
      options?.onError?.(new Error('실패'));
    });

    render(<CommunityNewScreen />);
    fireEvent.changeText(screen.getByLabelText('이름'), '주말 축구 모임');
    fireEvent.press(screen.getByLabelText('⚽ 축구'));
    fireEvent.press(screen.getByLabelText('개설하기'));

    expect(screen.getByRole('alert')).toBeTruthy();
    expect(screen.getByLabelText('개설하기').props.accessibilityState.disabled).toBe(false);
  });

  it('종목 선택 칩 그룹이 radiogroup으로 노출된다', () => {
    render(<CommunityNewScreen />);

    expect(screen.getByLabelText('종목 목록').props.accessibilityRole).toBe('radiogroup');
  });

  it('다크 모드에서도 폼이 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<CommunityNewScreen />);

    expect(screen.getByLabelText('이름')).toBeTruthy();
  });
});
