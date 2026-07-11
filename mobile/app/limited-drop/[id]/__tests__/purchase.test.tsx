/**
 * LimitedDropPurchaseScreen(S2) — 수량 선택 → 구매 확정 → phase별 결과 UI를 사용자 관점으로 검증.
 * 근거: FE-07 티켓 "테스트 케이스" · design-fe-app.md "S2 텍스트 와이어프레임" · "화면별 상태 표"
 *
 * useLimitedDrop·usePurchaseLimitedDrop을 모킹해 화면의 phase 분기 렌더링만 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { LimitedDropResponse } from '../../../../api/types';
import LimitedDropPurchaseScreen from '../purchase';

jest.mock('../../../../lib/useLimitedDrop', () => ({
  useLimitedDrop: jest.fn(),
}));
jest.mock('../../../../lib/usePurchaseLimitedDrop', () => ({
  usePurchaseLimitedDrop: jest.fn(),
}));

import { useLocalSearchParams, useRouter } from 'expo-router';
import { useLimitedDrop } from '../../../../lib/useLimitedDrop';
import { usePurchaseLimitedDrop } from '../../../../lib/usePurchaseLimitedDrop';

const useLimitedDropMock = useLimitedDrop as jest.MockedFunction<typeof useLimitedDrop>;
const usePurchaseLimitedDropMock = usePurchaseLimitedDrop as jest.MockedFunction<
  typeof usePurchaseLimitedDrop
>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;

const baseDrop: LimitedDropResponse = {
  dropId: 1,
  productId: 100,
  status: 'OPEN',
  openAt: '2026-07-05T20:00:00Z',
  closeAt: '2026-07-06T20:00:00Z',
  remaining: 32,
  perUserLimit: 2,
  totalQuantity: 100,
  price: 89000,
};

function mockUseLimitedDropReturn(overrides: Partial<ReturnType<typeof useLimitedDrop>>) {
  useLimitedDropMock.mockReturnValue({
    data: baseDrop,
    isLoading: false,
    isError: false,
    error: null,
    refetch: jest.fn(),
    ...overrides,
  } as ReturnType<typeof useLimitedDrop>);
}

function mockUsePurchaseLimitedDropReturn(
  mutate: jest.Mock,
  overrides: Partial<ReturnType<typeof usePurchaseLimitedDrop>>
) {
  usePurchaseLimitedDropMock.mockReturnValue({
    mutate,
    mutateAsync: jest.fn(),
    isPending: false,
    isSuccess: false,
    isError: false,
    data: undefined,
    reset: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof usePurchaseLimitedDrop>);
}

describe('LimitedDropPurchaseScreen', () => {
  let pushMock: jest.Mock;

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useLocalSearchParamsMock.mockReturnValue({ id: '1' });
    pushMock = jest.fn();
    useRouterMock.mockReturnValue({
      push: pushMock,
      replace: jest.fn(),
      back: jest.fn(),
    } as unknown as ReturnType<typeof useRouter>);
    mockUseLimitedDropReturn({});
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('증가 버튼은 perUserLimit을 초과해 수량을 늘리지 않는다', () => {
    mockUsePurchaseLimitedDropReturn(jest.fn(), {});

    render(<LimitedDropPurchaseScreen />);

    const increaseButton = screen.getByLabelText('수량 증가');
    fireEvent.press(increaseButton);
    expect(screen.getByText('2')).toBeTruthy();
    expect(increaseButton.props.accessibilityState.disabled).toBe(true);

    fireEvent.press(increaseButton);
    expect(screen.getByText('2')).toBeTruthy();
  });

  it('구매 확정을 누르면 선택한 수량으로 mutate를 호출한다', () => {
    const mutateMock = jest.fn();
    mockUsePurchaseLimitedDropReturn(mutateMock, {});

    render(<LimitedDropPurchaseScreen />);
    fireEvent.press(screen.getByLabelText('수량 증가'));
    fireEvent.press(screen.getByLabelText('구매 확정'));

    expect(mutateMock).toHaveBeenCalledWith({ quantity: 2 });
  });

  it('제출 중에는 처리 중 오버레이를 표시하고 구매 확정 버튼을 감춘다', () => {
    mockUsePurchaseLimitedDropReturn(jest.fn(), { isPending: true });

    render(<LimitedDropPurchaseScreen />);

    expect(screen.getByLabelText('구매 처리 중')).toBeTruthy();
    expect(screen.queryByLabelText('구매 확정')).toBeNull();
  });

  it('admitted phase에서 주문번호를 안내하고 결제하기를 누르면 결제 화면으로 이동한다', () => {
    mockUsePurchaseLimitedDropReturn(jest.fn(), {
      isSuccess: true,
      data: { phase: 'admitted', data: { orderId: 42, dropId: 1, status: 'PENDING' } },
    });

    render(<LimitedDropPurchaseScreen />);

    expect(screen.getByText(/주문번호 42/)).toBeTruthy();
    fireEvent.press(screen.getByLabelText('결제하기'));

    expect(pushMock).toHaveBeenCalledWith(expect.stringContaining('orderId=42'));
  });

  it('soldOut phase에서 마감 안내를 표시한다(오류 톤 아님)', () => {
    mockUsePurchaseLimitedDropReturn(jest.fn(), {
      isSuccess: true,
      data: { phase: 'soldOut' },
    });

    render(<LimitedDropPurchaseScreen />);

    expect(screen.getByText('아쉽게도 마감됐어요')).toBeTruthy();
    expect(screen.getByLabelText('상세로')).toBeTruthy();
  });

  it('tooEarly phase에서 판매 시작 시각을 안내한다', () => {
    mockUsePurchaseLimitedDropReturn(jest.fn(), {
      isSuccess: true,
      data: { phase: 'tooEarly', openAt: '2026-07-05T20:00:00Z' },
    });

    render(<LimitedDropPurchaseScreen />);

    expect(screen.getByText(/2026-07-05T20:00:00Z/)).toBeTruthy();
  });

  it('throttled phase에서 재시도 안내를 표시하고 지금 재시도를 누르면 다시 mutate한다', () => {
    const mutateMock = jest.fn();
    mockUsePurchaseLimitedDropReturn(mutateMock, {
      isSuccess: true,
      data: { phase: 'throttled' },
    });

    render(<LimitedDropPurchaseScreen />);

    expect(screen.getByText('접속이 몰리고 있어요')).toBeTruthy();
    fireEvent.press(screen.getByLabelText('지금 재시도'));

    expect(mutateMock).toHaveBeenCalledWith({ quantity: 1 });
  });

  it('limit phase에서 1인당 구매 한도를 안내한다', () => {
    mockUsePurchaseLimitedDropReturn(jest.fn(), {
      isSuccess: true,
      data: { phase: 'limit' },
    });

    render(<LimitedDropPurchaseScreen />);

    expect(screen.getByText('1인당 2개까지 구매할 수 있어요')).toBeTruthy();
  });

  it('error phase에서 일반 오류 안내와 다시 시도를 표시한다', () => {
    const mutateMock = jest.fn();
    mockUsePurchaseLimitedDropReturn(mutateMock, {
      isSuccess: true,
      data: { phase: 'error' },
    });

    render(<LimitedDropPurchaseScreen />);

    expect(screen.getByText('일시 오류예요')).toBeTruthy();
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(mutateMock).toHaveBeenCalledWith({ quantity: 1 });
  });

  it('bypassDenied phase(403 QUEUE_BYPASS_DENIED)에서 다시 대기하기 안내를 표시하고 누르면 대기실로 재진입한다', () => {
    const replaceMock = jest.fn();
    useRouterMock.mockReturnValue({
      push: pushMock,
      replace: replaceMock,
      back: jest.fn(),
    } as unknown as ReturnType<typeof useRouter>);
    mockUsePurchaseLimitedDropReturn(jest.fn(), {
      isSuccess: true,
      data: { phase: 'bypassDenied' },
    });

    render(<LimitedDropPurchaseScreen />);

    expect(screen.getByText('대기 시간이 지났어요')).toBeTruthy();
    fireEvent.press(screen.getByLabelText('다시 대기하기'));

    expect(replaceMock).toHaveBeenCalledWith('/queue/limited-drop/1');
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    mockUsePurchaseLimitedDropReturn(jest.fn(), {});

    render(<LimitedDropPurchaseScreen />);

    expect(screen.getByLabelText('구매 확정')).toBeTruthy();
  });
});
