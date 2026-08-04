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
    locationName: null,
    scheduledAt: null,
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
    render(
      <CatalogItemCard
        item={buildItem({ itemType: 'PRODUCT', sellerType: 'B2B' })}
        onPress={jest.fn()}
      />
    );

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

  // 회귀 방지: 11-통합-카탈로그에서 시설 4곳이 같은 이름의 프로그램을 등록해 "주말 정기 레슨"
  // 카드가 3회 반복돼 보이는 결함 — locationName으로 어느 시설의 프로그램인지 구분한다.
  it('locationName이 있으면 구분 정보 줄을 렌더한다(PROGRAM: 시설명)', () => {
    render(
      <CatalogItemCard
        item={buildItem({
          itemType: 'PROGRAM',
          locationName: '루틴 피트니스 강남점',
          scheduledAt: null,
        })}
        onPress={jest.fn()}
      />
    );

    expect(screen.getByText('루틴 피트니스 강남점')).toBeTruthy();
  });

  it('scheduledAt이 있으면 절대 일시로 포맷해 구분 정보 줄을 렌더한다(RECRUITMENT: 모임 활동 일시)', () => {
    render(
      <CatalogItemCard
        item={buildItem({
          itemType: 'RECRUITMENT',
          locationName: null,
          scheduledAt: '2026-08-10T19:00:00+09:00',
        })}
        onPress={jest.fn()}
      />
    );

    expect(screen.getByText('8월 10일 19:00')).toBeTruthy();
  });

  it('locationName·scheduledAt이 모두 있으면 한 줄에 이어붙여 렌더한다(TICKET: 경기장·시작 일시)', () => {
    render(
      <CatalogItemCard
        item={buildItem({
          itemType: 'TICKET',
          price: null,
          sellerType: null,
          locationName: '잠실종합운동장',
          scheduledAt: '2026-08-10T19:00:00+09:00',
        })}
        onPress={jest.fn()}
      />
    );

    expect(screen.getByText('잠실종합운동장 · 8월 10일 19:00')).toBeTruthy();
  });

  it('locationName·scheduledAt이 모두 null이면 구분 정보 줄을 렌더하지 않는다(PRODUCT·LIMITED_DROP)', () => {
    render(
      <CatalogItemCard
        item={buildItem({ locationName: null, scheduledAt: null })}
        onPress={jest.fn()}
      />
    );

    expect(screen.queryByTestId('catalog-item-card-distinguisher')).toBeNull();
  });
});
