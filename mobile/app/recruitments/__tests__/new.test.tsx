/**
 * RecruitmentNewScreen(A-R3) — 개설 폼 유효성·제출 결과 검증.
 * 근거: design-fe-app.md Testing Plan "개설 폼"(유효 입력 제출 성공 / 정원<1·참가비<0
 * 인라인 검증 / 마감<활동일 검증 / 제출 실패 토스트).
 *
 * useCreateRecruitment를 모킹해 화면 배선(CTA 활성화·이동·실패 인라인)만 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { RecruitmentResponse } from '../../../api/recruitment';
import RecruitmentNewScreen from '../new';

jest.mock('../../../lib/useRecruitment', () => ({
  useCreateRecruitment: jest.fn(),
}));

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: jest.fn(() => ({})),
}));

import { router } from 'expo-router';
import { useCreateRecruitment } from '../../../lib/useRecruitment';

const useCreateRecruitmentMock = useCreateRecruitment as jest.MockedFunction<
  typeof useCreateRecruitment
>;

const CREATED_RECRUITMENT: RecruitmentResponse = {
  id: 5,
  title: '주말 축구 3명 모집',
  description: null,
  capacity: 3,
  feeAmount: 5000,
  activityAt: '2026-07-12T14:00:00+09:00',
  applicationDeadline: '2026-07-10T23:00:00+09:00',
  communityId: null,
  recruiterUserId: 10,
  status: 'OPEN',
};

function mockCreateRecruitment(
  mutate: jest.Mock,
  overrides: Partial<ReturnType<typeof useCreateRecruitment>> = {}
) {
  useCreateRecruitmentMock.mockReturnValue({
    mutate,
    isPending: false,
    ...overrides,
  } as unknown as ReturnType<typeof useCreateRecruitment>);
}

function fillValidForm() {
  fireEvent.changeText(screen.getByLabelText('제목'), '주말 축구 3명 모집');
  fireEvent.changeText(screen.getByLabelText('정원'), '3');
  fireEvent.changeText(screen.getByLabelText('참가비'), '5000');
  fireEvent.changeText(screen.getByLabelText('활동일시'), '2026-07-12T14:00');
  fireEvent.changeText(screen.getByLabelText('신청마감'), '2026-07-10T23:00');
}

describe('RecruitmentNewScreen', () => {
  let createMock: jest.Mock;

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    createMock = jest.fn();
    mockCreateRecruitment(createMock);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('필수값을 입력하지 않으면 개설 CTA가 비활성이다', () => {
    render(<RecruitmentNewScreen />);

    expect(screen.getByLabelText('개설하기').props.accessibilityState.disabled).toBe(true);
  });

  it('유효한 값을 채우면 개설 CTA가 활성화된다', () => {
    render(<RecruitmentNewScreen />);
    fillValidForm();

    expect(screen.getByLabelText('개설하기').props.accessibilityState.disabled).toBe(false);
  });

  it('정원이 0이면 인라인 오류가 표시되고 CTA가 비활성 상태를 유지한다', () => {
    render(<RecruitmentNewScreen />);
    fillValidForm();
    fireEvent.changeText(screen.getByLabelText('정원'), '0');

    expect(screen.getByText('정원은 1명 이상의 정수로 입력해주세요')).toBeTruthy();
    expect(screen.getByLabelText('개설하기').props.accessibilityState.disabled).toBe(true);
  });

  it('참가비가 음수면 인라인 오류가 표시된다', () => {
    render(<RecruitmentNewScreen />);
    fillValidForm();
    fireEvent.changeText(screen.getByLabelText('참가비'), '-1');

    expect(screen.getByText('참가비는 0원 이상으로 입력해주세요')).toBeTruthy();
  });

  it('신청마감이 활동일보다 늦으면 인라인 오류가 표시된다', () => {
    render(<RecruitmentNewScreen />);
    fillValidForm();
    fireEvent.changeText(screen.getByLabelText('활동일시'), '2026-07-10T00:00');
    fireEvent.changeText(screen.getByLabelText('신청마감'), '2026-07-12T00:00');

    expect(screen.getByText('신청마감은 활동일보다 이전이어야 해요')).toBeTruthy();
  });

  it('개설 성공 시 상세 화면으로 이동한다', () => {
    createMock.mockImplementation((_request, options) => {
      options?.onSuccess?.(CREATED_RECRUITMENT);
    });

    render(<RecruitmentNewScreen />);
    fillValidForm();
    fireEvent.press(screen.getByLabelText('개설하기'));

    expect(router.replace).toHaveBeenCalledWith('/recruitments/5');
  });

  it('개설 실패 시 인라인 오류 토스트가 표시된다', () => {
    createMock.mockImplementation((_request, options) => {
      options?.onError?.(new Error('실패'));
    });

    render(<RecruitmentNewScreen />);
    fillValidForm();
    fireEvent.press(screen.getByLabelText('개설하기'));

    expect(screen.getByRole('alert')).toBeTruthy();
  });

  it('다크 모드에서도 폼이 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<RecruitmentNewScreen />);

    expect(screen.getByLabelText('제목')).toBeTruthy();
  });
});
