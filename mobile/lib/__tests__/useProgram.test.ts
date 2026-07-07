/**
 * usePrograms가 facilityId로 시설상품 목록을 반환한다
 * usePrograms는 facilityId가 비어있으면 쿼리를 실행하지 않는다
 * 등록된 상품이 없으면 빈 배열을 반환한다(정상)
 * facility.program.enabled 플래그 OFF(404)는 에러 상태로 전파된다
 */
import { createElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';

import { programsQueryKey, usePrograms } from '../useProgram';
import type { ProgramResponse } from '../../api/program';

jest.mock('../../api/program', () => ({
  listPrograms: jest.fn(),
}));

import { listPrograms } from '../../api/program';

const listProgramsMock = listPrograms as jest.MockedFunction<typeof listPrograms>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return { wrapper };
}

const mockProgram: ProgramResponse = {
  id: 1,
  facilityId: 'facility-1',
  ownerUserId: 42,
  name: 'PT 1:1',
  description: null,
  price: 50000,
  capacity: 1,
  durationMinutes: 60,
};

describe('usePrograms', () => {
  afterEach(() => jest.clearAllMocks());

  it('facilityId로 시설상품 목록을 반환한다', async () => {
    listProgramsMock.mockResolvedValue([mockProgram]);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => usePrograms('facility-1'), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([mockProgram]);
    expect(listProgramsMock).toHaveBeenCalledWith('facility-1');
  });

  it('facilityId가 비어있으면 쿼리를 실행하지 않는다', () => {
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => usePrograms(''), { wrapper });

    expect(result.current.fetchStatus).toBe('idle');
    expect(listProgramsMock).not.toHaveBeenCalled();
  });

  it('등록된 상품이 없으면 빈 배열을 반환한다(정상)', async () => {
    listProgramsMock.mockResolvedValue([]);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => usePrograms('facility-2'), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([]);
  });

  it('facility.program.enabled 플래그 OFF(404)는 에러 상태로 전파된다', async () => {
    listProgramsMock.mockRejectedValue(Object.assign(new Error('Not Found'), { status: 404 }));
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => usePrograms('facility-3'), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('programsQueryKey는 facilityId로 결정적인 키를 만든다', () => {
    expect(programsQueryKey('facility-1')).toEqual(['facilities', 'facility-1', 'programs']);
  });
});
