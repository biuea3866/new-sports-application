/**
 * EventOrderScreen — 좌석 선점·결제 확정 흐름 + 가상 대기열 403 재대기 안내 검증(FE-09).
 * 근거: FE-09 티켓 "테스트 케이스", design-fe-app.md "화면별 상태 표"(error—토큰 만료) · 시나리오 3.
 *
 * api/ticketOrders 함수를 모킹해 화면 배선(성공 시 결제 이동, 일반 오류 시 재시도 안내,
 * QUEUE_BYPASS_DENIED 시 대기실 재진입 안내)을 사용자 관점으로 검증한다.
 */
import React from 'react';
import { Alert } from 'react-native';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { AxiosError } from 'axios';

import type {
  EventDetailResponse,
  SelectSeatsResponse,
  TicketOrderResponse,
} from '../../../../api/types';
import EventOrderScreen from '../order';

jest.mock('../../../../lib/useEvent', () => ({
  useEvent: jest.fn(),
}));

jest.mock('../../../../api/ticketOrders', () => ({
  selectSeats: jest.fn(),
  releaseSeats: jest.fn(),
  purchaseTicketOrder: jest.fn(),
  isQueueBypassDeniedError: jest.requireActual('../../../../api/ticketOrders')
    .isQueueBypassDeniedError,
}));

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), back: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: jest.fn(),
}));

import { router, useLocalSearchParams } from 'expo-router';
import { purchaseTicketOrder, releaseSeats, selectSeats } from '../../../../api/ticketOrders';
import { useEvent } from '../../../../lib/useEvent';

const useEventMock = useEvent as jest.MockedFunction<typeof useEvent>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;
const selectSeatsMock = selectSeats as jest.MockedFunction<typeof selectSeats>;
const purchaseTicketOrderMock = purchaseTicketOrder as jest.MockedFunction<
  typeof purchaseTicketOrder
>;
const releaseSeatsMock = releaseSeats as jest.MockedFunction<typeof releaseSeats>;

const baseEvent: EventDetailResponse = {
  id: 1,
  title: '2026 서울 마라톤',
  venue: '잠실 종합운동장',
  startsAt: '2026-08-01T09:00:00Z',
  status: 'OPEN',
  sections: [{ section: 'A', totalSeats: 100 }],
  seats: [{ id: 10, section: 'A', rowNo: '1', seatNo: '1', price: '50000', available: true }],
};

const mockSelectResponse: SelectSeatsResponse = {
  lockId: '1:10',
  expiresAt: '2099-01-01T00:05:00Z',
};

const mockOrderResponse: TicketOrderResponse = {
  ticketOrderId: 99,
  status: 'PENDING',
};

function queueBypassDeniedError(): AxiosError {
  return new AxiosError('Forbidden', undefined, undefined, undefined, {
    status: 403,
    data: { code: 'QUEUE_BYPASS_DENIED' },
    statusText: 'Forbidden',
    headers: {},
    config: {} as never,
  });
}

describe('EventOrderScreen', () => {
  let alertSpy: jest.SpyInstance;

  beforeEach(() => {
    useLocalSearchParamsMock.mockReturnValue({ id: '1', seatIds: '10' });
    useEventMock.mockReturnValue({
      data: baseEvent,
      isLoading: false,
      isError: false,
      refetch: jest.fn(),
    } as unknown as ReturnType<typeof useEvent>);
    alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
  });

  afterEach(() => {
    jest.clearAllMocks();
    alertSpy.mockRestore();
  });

  it('선점·구매 성공 시 결제 화면으로 이동한다', async () => {
    selectSeatsMock.mockResolvedValue(mockSelectResponse);
    purchaseTicketOrderMock.mockResolvedValue(mockOrderResponse);

    render(<EventOrderScreen />);
    fireEvent.press(screen.getByLabelText('결제하기'));

    await waitFor(() =>
      expect(router.push).toHaveBeenCalledWith(
        expect.stringContaining('/payment/new?orderType=TICKETING&orderId=99')
      )
    );
  });

  it('선점 성공 후 구매가 일반 오류(500)로 실패하면 release가 호출되고 일반 오류 안내가 표시된다', async () => {
    selectSeatsMock.mockResolvedValue(mockSelectResponse);
    purchaseTicketOrderMock.mockRejectedValue(new Error('Internal Server Error'));

    render(<EventOrderScreen />);
    fireEvent.press(screen.getByLabelText('결제하기'));

    await waitFor(() => expect(releaseSeatsMock).toHaveBeenCalledWith(1, [10]));
    expect(alertSpy).toHaveBeenCalledWith('오류', 'Internal Server Error');
  });

  it('좌석 선점이 403 QUEUE_BYPASS_DENIED로 거부되면 "다시 대기하기" 안내 후 대기실로 이동한다', async () => {
    selectSeatsMock.mockRejectedValue(queueBypassDeniedError());

    render(<EventOrderScreen />);
    fireEvent.press(screen.getByLabelText('결제하기'));

    await waitFor(() => expect(alertSpy).toHaveBeenCalled());
    const [title, message, buttons] = alertSpy.mock.calls[0];
    expect(title).toBe('대기 시간이 지났어요');
    expect(message).toBe('다시 대기하기');

    buttons[0].onPress();

    expect(router.replace).toHaveBeenCalledWith('/queue/ticketing-event/1');
  });
});
