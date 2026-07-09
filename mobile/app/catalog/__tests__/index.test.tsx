/**
 * CatalogScreen(`/catalog`) — 통합 상품 검색 화면 4상태 + 항목 탭 이동 검증.
 * 근거: FE-09 티켓 "테스트 케이스", design-fe-app.md Testing Plan "CatalogScreen".
 *
 * useCatalogSearch를 모킹해 화면 배선(4상태 분기·부분 실패 배너·탭 이동)만
 * 사용자 관점으로 검증한다. 이미 머지된 CatalogSearchControls·CatalogItemCard·
 * PartialFailureBanner·catalog-navigation은 실제 구현을 그대로 사용한다.
 */
import React from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { CatalogItem, CatalogSearchResponse } from '../../../api/catalog-types';
import CatalogScreen from '../index';

jest.mock('../../../lib/useCatalogSearch', () => ({
  useCatalogSearch: jest.fn(),
}));

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), back: jest.fn() },
}));

import { router } from 'expo-router';
import { useCatalogSearch } from '../../../lib/useCatalogSearch';
import { lightTokens, darkTokens } from '../../../theme/tokens';

const useCatalogSearchMock = useCatalogSearch as jest.MockedFunction<typeof useCatalogSearch>;

function buildItem(overrides: Partial<CatalogItem> = {}): CatalogItem {
  return {
    itemType: 'PRODUCT',
    sourceId: 123,
    title: '요가매트 프리미엄',
    price: 32000,
    sellerType: 'B2C',
    status: 'ON_SALE',
    detailPath: '/products/123',
    createdAt: '2026-07-06T00:00:00+09:00',
    ...overrides,
  };
}

function buildCatalogResponse(
  overrides: Partial<CatalogSearchResponse> = {}
): CatalogSearchResponse {
  const base: CatalogSearchResponse = { items: [], page: 0, size: 20, failedDomains: [] };
  return { ...base, ...overrides };
}

interface MockCatalogSearchOverrides {
  data?: Partial<CatalogSearchResponse>;
  isLoading?: boolean;
  isError?: boolean;
  refetch?: jest.Mock;
}

function mockCatalogSearch(overrides: MockCatalogSearchOverrides = {}) {
  const { data, isLoading = false, isError = false, refetch = jest.fn() } = overrides;
  useCatalogSearchMock.mockReturnValue({
    data: data ? buildCatalogResponse(data) : undefined,
    isLoading,
    isError,
    refetch,
  } as unknown as ReturnType<typeof useCatalogSearch>);
}

describe('CatalogScreen', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('로딩 시 스켈레톤을 보여준다', () => {
    mockCatalogSearch({ isLoading: true });

    render(<CatalogScreen />);

    expect(screen.getByLabelText('로딩 중')).toBeTruthy();
  });

  it('결과가 0건이면 empty 문구를 보여준다', () => {
    mockCatalogSearch({ data: { items: [] } });

    render(<CatalogScreen />);

    expect(screen.getByText('검색 결과가 없어요')).toBeTruthy();
  });

  it('요청 실패면 에러 메시지와 재시도 버튼을 보여준다', () => {
    const refetchMock = jest.fn();
    mockCatalogSearch({ isError: true, refetch: refetchMock });

    render(<CatalogScreen />);
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(screen.getByText('검색 결과를 불러오지 못했어요')).toBeTruthy();
    expect(refetchMock).toHaveBeenCalled();
  });

  it('성공 시 CatalogItemCard 리스트를 보여준다', () => {
    mockCatalogSearch({
      data: {
        items: [
          buildItem({ itemType: 'PRODUCT', sourceId: 123, title: '요가매트 프리미엄' }),
          buildItem({
            itemType: 'PROGRAM',
            sourceId: 456,
            title: '아침 요가 클래스',
            sellerType: null,
          }),
        ],
      },
    });

    render(<CatalogScreen />);

    expect(screen.getByText('요가매트 프리미엄')).toBeTruthy();
    expect(screen.getByText('아침 요가 클래스')).toBeTruthy();
  });

  it('failedDomains가 있으면 부분 실패 배너와 결과를 함께 보여준다', () => {
    mockCatalogSearch({
      data: {
        items: [buildItem()],
        failedDomains: ['TICKET'],
      },
    });

    render(<CatalogScreen />);

    expect(screen.getByText('일부 결과를 불러오지 못했어요')).toBeTruthy();
    expect(within(screen.getByTestId('partial-failure-banner')).getByText('티켓')).toBeTruthy();
    expect(screen.getByText('요가매트 프리미엄')).toBeTruthy();
  });

  it('항목을 탭하면 itemType 매핑 경로로 이동한다', () => {
    mockCatalogSearch({
      data: { items: [buildItem({ itemType: 'PRODUCT', sourceId: 123 })] },
    });

    render(<CatalogScreen />);
    fireEvent.press(screen.getByRole('button', { name: /요가매트 프리미엄/ }));

    expect(router.push).toHaveBeenCalledWith('/product/123');
  });

  it('라이트 모드에서 하드코딩 색 없이 토큰으로 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('light');
    mockCatalogSearch({ data: { items: [buildItem()] } });

    render(<CatalogScreen />);

    expect(screen.getByTestId('catalog-screen')).toHaveStyle({
      backgroundColor: lightTokens.background,
    });
  });

  it('다크 모드에서 하드코딩 색 없이 토큰으로 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    mockCatalogSearch({ data: { items: [buildItem()] } });

    render(<CatalogScreen />);

    expect(screen.getByTestId('catalog-screen')).toHaveStyle({
      backgroundColor: darkTokens.background,
    });
  });
});
