/**
 * useCatalogSearch — GET /api/catalog TanStack Query 훅 검증.
 */
import { createElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';

import type { CatalogSearchCriteria, CatalogSearchResponse } from '../../api/catalog-types';
import { useCatalogSearch } from '../useCatalogSearch';

jest.mock('../../api/catalog', () => ({
  getCatalog: jest.fn(),
}));

import { getCatalog } from '../../api/catalog';

const getCatalogMock = getCatalog as jest.MockedFunction<typeof getCatalog>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return { wrapper, queryClient };
}

const mockResponse: CatalogSearchResponse = {
  items: [
    {
      itemType: 'PRODUCT',
      sourceId: 1,
      title: '요가매트 프리미엄',
      price: 39000,
      sellerType: 'B2C',
      status: 'ACTIVE',
      detailPath: '/products/1',
      createdAt: '2026-07-01T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  failedDomains: [],
};

const mockResponseWithPartialFailure: CatalogSearchResponse = {
  items: [],
  page: 0,
  size: 20,
  failedDomains: ['TICKET', 'PROGRAM'],
};

describe('useCatalogSearch', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('keyword·itemType·sellerType이 요청 파라미터로 전달된다', async () => {
    getCatalogMock.mockResolvedValue(mockResponse);
    const { wrapper, queryClient } = createWrapper();
    const criteria: CatalogSearchCriteria = {
      keyword: '요가',
      itemType: 'PRODUCT',
      sellerType: 'B2C',
      page: 0,
      size: 20,
    };

    const { result, unmount } = renderHook(() => useCatalogSearch(criteria), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(getCatalogMock).toHaveBeenCalledWith(criteria);

    unmount();
    queryClient.clear();
  });

  it('파라미터가 바뀌면 새 queryKey로 재조회한다', async () => {
    getCatalogMock.mockResolvedValue(mockResponse);
    const { wrapper, queryClient } = createWrapper();

    const { result, rerender, unmount } = renderHook(
      (criteria: CatalogSearchCriteria) => useCatalogSearch(criteria),
      { wrapper, initialProps: { page: 0, size: 20 } as CatalogSearchCriteria }
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getCatalogMock).toHaveBeenCalledTimes(1);

    rerender({ keyword: '축구공', page: 0, size: 20 });

    await waitFor(() => expect(getCatalogMock).toHaveBeenCalledTimes(2));
    expect(getCatalogMock).toHaveBeenLastCalledWith({ keyword: '축구공', page: 0, size: 20 });

    unmount();
    queryClient.clear();
  });

  it('응답의 failedDomains를 그대로 반환한다', async () => {
    getCatalogMock.mockResolvedValue(mockResponseWithPartialFailure);
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(
      () => useCatalogSearch({ page: 0, size: 20 }),
      { wrapper }
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data?.failedDomains).toEqual(['TICKET', 'PROGRAM']);

    unmount();
    queryClient.clear();
  });

  it('조회 실패 시 isError 상태가 된다', async () => {
    getCatalogMock.mockRejectedValue(new Error('Network Error'));
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(
      () => useCatalogSearch({ page: 0, size: 20 }),
      { wrapper }
    );

    await waitFor(() => expect(result.current.isError).toBe(true));

    unmount();
    queryClient.clear();
  });
});
