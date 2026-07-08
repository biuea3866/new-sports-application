/**
 * getCatalog — GET /api/catalog 통합 검색 API 함수 테스트
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import { getCatalog } from '../catalog';
import type { CatalogSearchResponse } from '../catalog-types';

jest.mock('../be-client', () => {
  const actual = jest.requireActual<typeof import('../be-client')>('../be-client');
  const instance = actual.createBeClient('http://localhost:8080');
  return {
    ...actual,
    getBeClient: jest.fn(() => instance),
    _testInstance: instance,
  };
});

import * as beClientModule from '../be-client';

const testInstance = (
  beClientModule as unknown as { _testInstance: ReturnType<typeof createBeClient> }
)._testInstance;
const mock = new MockAdapter(testInstance);

afterEach(() => mock.reset());

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

describe('getCatalog', () => {
  it('keyword·itemType·sellerType·page·size가 요청 쿼리 파라미터로 전달된다', async () => {
    mock
      .onGet('/api/catalog', {
        params: {
          page: 0,
          size: 20,
          keyword: '요가',
          itemType: 'PRODUCT',
          sellerType: 'B2C',
        },
      })
      .reply(200, mockResponse);

    const result = await getCatalog({
      keyword: '요가',
      itemType: 'PRODUCT',
      sellerType: 'B2C',
      page: 0,
      size: 20,
    });

    expect(result.items).toHaveLength(1);
  });

  it('옵션 파라미터(keyword 미지정) 시 params에서 생략된다', async () => {
    mock
      .onGet('/api/catalog', {
        params: {
          page: 0,
          size: 20,
        },
      })
      .reply(200, mockResponse);

    const result = await getCatalog({ page: 0, size: 20 });

    expect(result.items).toHaveLength(1);
  });

  it('응답이 CatalogSearchResponse로 그대로 반환된다', async () => {
    mock.onGet('/api/catalog').reply(200, mockResponse);

    const result = await getCatalog({ page: 0, size: 20 });

    expect(result).toEqual(mockResponse);
    expect(result.items[0].detailPath).toBe('/products/1');
    expect(result.failedDomains).toEqual([]);
  });

  it('네트워크 오류 시 예외가 호출부로 전파된다', async () => {
    mock.onGet('/api/catalog').networkError();

    await expect(getCatalog({ page: 0, size: 20 })).rejects.toThrow();
  });
});
