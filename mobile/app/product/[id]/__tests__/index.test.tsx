/**
 * ProductDetailScreen — 한정판 진입점 배선 검증
 * 근거: FE-08 티켓 "테스트 케이스"
 *
 * 기존 화면(useProducts/useAddCartItem/useCurrentUserId)을 모킹해 신규 진입점만 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';

import type { ProductWithStock } from '../../../../api/goods';
import ProductDetailScreen from '../index';

jest.mock('../../../../api/goods', () => ({
  useProducts: jest.fn(),
  useAddCartItem: jest.fn(),
  useCurrentUserId: jest.fn(),
}));

jest.mock('../../../../lib/useChat', () => ({
  useStartGoodsChat: jest.fn(),
}));

import { useLocalSearchParams, useRouter } from 'expo-router';
import { useAddCartItem, useCurrentUserId, useProducts } from '../../../../api/goods';
import { useStartGoodsChat } from '../../../../lib/useChat';

const useProductsMock = useProducts as jest.MockedFunction<typeof useProducts>;
const useAddCartItemMock = useAddCartItem as jest.MockedFunction<typeof useAddCartItem>;
const useCurrentUserIdMock = useCurrentUserId as jest.MockedFunction<typeof useCurrentUserId>;
const useStartGoodsChatMock = useStartGoodsChat as jest.MockedFunction<typeof useStartGoodsChat>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;

const productWithDrop: ProductWithStock = {
  id: 1,
  name: '나이키 한정판 스니커즈',
  category: 'APPAREL',
  price: 129000,
  description: '한정판 스니커즈입니다.',
  imageUrl: 'https://example.com/img.jpg',
  status: 'ACTIVE',
  stockQuantity: 10,
  limitedDropId: 5,
};

const productWithoutDrop: ProductWithStock = {
  ...productWithDrop,
  id: 2,
  limitedDropId: undefined,
};

function mockUseProductsReturn(products: ProductWithStock[]) {
  useProductsMock.mockReturnValue({
    data: products,
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
  } as unknown as ReturnType<typeof useProducts>);
}

describe('ProductDetailScreen 한정판 진입점', () => {
  let pushMock: jest.Mock;

  beforeEach(() => {
    pushMock = jest.fn();
    useRouterMock.mockReturnValue({
      push: pushMock,
      replace: jest.fn(),
      back: jest.fn(),
    } as unknown as ReturnType<typeof useRouter>);
    useAddCartItemMock.mockReturnValue({
      mutate: jest.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useAddCartItem>);
    useCurrentUserIdMock.mockReturnValue(1);
    useStartGoodsChatMock.mockReturnValue({
      mutate: jest.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useStartGoodsChat>);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('한정판 회차가 있는 상품에는 진입점이 노출된다', () => {
    useLocalSearchParamsMock.mockReturnValue({ id: '1' });
    mockUseProductsReturn([productWithDrop]);

    render(<ProductDetailScreen />);

    expect(screen.getByLabelText('한정판 구매하러 가기')).toBeTruthy();
  });

  it('한정판 회차가 없는 상품에는 진입점이 노출되지 않는다', () => {
    useLocalSearchParamsMock.mockReturnValue({ id: '2' });
    mockUseProductsReturn([productWithoutDrop]);

    render(<ProductDetailScreen />);

    expect(screen.queryByLabelText('한정판 구매하러 가기')).toBeNull();
  });

  it('진입점 탭 시 한정판 상세 화면으로 이동한다', () => {
    useLocalSearchParamsMock.mockReturnValue({ id: '1' });
    mockUseProductsReturn([productWithDrop]);

    render(<ProductDetailScreen />);
    fireEvent.press(screen.getByLabelText('한정판 구매하러 가기'));

    expect(pushMock).toHaveBeenCalledWith('/limited-drop/5');
  });

  it('imageUrl이 있으면 상품 이미지를 렌더하고 placeholder 문구를 숨긴다', () => {
    useLocalSearchParamsMock.mockReturnValue({ id: '1' });
    mockUseProductsReturn([productWithDrop]);

    render(<ProductDetailScreen />);

    expect(
      screen.getByTestId('product-detail-image', { includeHiddenElements: true })
    ).toBeTruthy();
    expect(screen.queryByText('상품 이미지')).toBeNull();
  });

  it('imageUrl이 없으면 placeholder 문구를 렌더한다', () => {
    useLocalSearchParamsMock.mockReturnValue({ id: '2' });
    mockUseProductsReturn([{ ...productWithoutDrop, imageUrl: '' }]);

    render(<ProductDetailScreen />);

    expect(screen.getByText('상품 이미지', { includeHiddenElements: true })).toBeTruthy();
    expect(screen.queryByTestId('product-detail-image')).toBeNull();
  });
});
