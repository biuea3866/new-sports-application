/**
 * CatalogItemCard — 통합 검색 결과 한 항목(CatalogItem)을 렌더하는 프레젠테이션 카드.
 * 근거: `20260708-상품주문-공유상위컨텍스트-design-fe-app.md` "텍스트 와이어프레임 ①".
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { CatalogItemCard } from '../CatalogItemCard';
import type { CatalogItem } from '../../../api/catalog-types';

jest.mock('../../../theme/useTheme', () => ({
  useTheme: jest.fn(),
}));

import { useTheme } from '../../../theme/useTheme';
import { lightTokens, darkTokens } from '../../../theme/tokens';

const useThemeMock = useTheme as jest.MockedFunction<typeof useTheme>;

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

describe('CatalogItemCard', () => {
  beforeEach(() => {
    useThemeMock.mockReturnValue({ scheme: 'light', tokens: lightTokens });
  });

  it('제목과 가격(KRW 포맷)을 렌더한다', () => {
    render(<CatalogItemCard item={buildItem()} onPress={jest.fn()} />);

    expect(screen.getByText('요가매트 프리미엄')).toBeTruthy();
    expect(screen.getByText('32,000원')).toBeTruthy();
  });

  it('price=null이면 가격 상세 확인을 렌더한다', () => {
    render(
      <CatalogItemCard
        item={buildItem({ itemType: 'TICKET', price: null, sellerType: null })}
        onPress={jest.fn()}
      />
    );

    expect(screen.getByText('가격 상세 확인')).toBeTruthy();
  });

  it('PRODUCT 항목이면 sellerType 배지를 렌더한다', () => {
    render(<CatalogItemCard item={buildItem({ itemType: 'PRODUCT', sellerType: 'B2B' })} onPress={jest.fn()} />);

    expect(screen.getByText('브랜드')).toBeTruthy();
  });

  it('PRODUCT가 아닌 항목이면 sellerType 배지를 렌더하지 않는다', () => {
    render(
      <CatalogItemCard
        item={buildItem({ itemType: 'PROGRAM', sellerType: null })}
        onPress={jest.fn()}
      />
    );

    expect(screen.queryByText('브랜드')).toBeNull();
    expect(screen.queryByText('중고')).toBeNull();
  });

  it('탭하면 onPress가 항목 detailPath와 함께 호출된다', () => {
    const onPress = jest.fn();
    const item = buildItem({ detailPath: '/products/123' });
    render(<CatalogItemCard item={item} onPress={onPress} />);

    fireEvent.press(screen.getByRole('button'));

    expect(onPress).toHaveBeenCalledWith('/products/123');
  });

  it('itemType 라벨 배지를 렌더한다', () => {
    render(<CatalogItemCard item={buildItem({ itemType: 'RECRUITMENT' })} onPress={jest.fn()} />);

    expect(screen.getByText('모집')).toBeTruthy();
  });

  it('라이트 모드에서 하드코딩 색 없이 토큰으로 렌더된다', () => {
    render(<CatalogItemCard item={buildItem()} onPress={jest.fn()} />);

    expect(screen.getByTestId('catalog-item-card-PRODUCT-123')).toHaveStyle({
      backgroundColor: lightTokens.surface,
    });
  });

  it('다크 모드에서 하드코딩 색 없이 토큰으로 렌더된다', () => {
    useThemeMock.mockReturnValue({ scheme: 'dark', tokens: darkTokens });

    render(<CatalogItemCard item={buildItem()} onPress={jest.fn()} />);

    expect(screen.getByTestId('catalog-item-card-PRODUCT-123')).toHaveStyle({
      backgroundColor: darkTokens.surface,
    });
  });
});
