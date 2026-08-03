/**
 * 장바구니 화면 — 항목이 상품명과 금액을 렌더하는지 검증한다.
 *
 * 회귀 배경: 장바구니 항목이 상품명 대신 "상품 #121"·"상품 #122" 같은 내부 식별자로 렌더됐다
 * (유즈케이스 캡쳐 12-장바구니). 같은 앱의 주문 내역·스토어는 상품명이 정상이었고,
 * 장바구니 응답만 productId·quantity 로 축소돼 있던 것이 원인이다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';

import CartScreen from '../index';
import type { CartDto } from '../../../api/goods';

jest.mock('../../../api/goods', () => ({
  useCart: jest.fn(),
  useUpdateCartItem: jest.fn(),
  useRemoveCartItem: jest.fn(),
  useCreateGoodsOrder: jest.fn(),
  useCurrentUserId: jest.fn(() => 3),
}));

import {
  useCart,
  useUpdateCartItem,
  useRemoveCartItem,
  useCreateGoodsOrder,
} from '../../../api/goods';

const useCartMock = useCart as jest.MockedFunction<typeof useCart>;
const useUpdateCartItemMock = useUpdateCartItem as jest.MockedFunction<typeof useUpdateCartItem>;
const useRemoveCartItemMock = useRemoveCartItem as jest.MockedFunction<typeof useRemoveCartItem>;
const useCreateGoodsOrderMock = useCreateGoodsOrder as jest.MockedFunction<
  typeof useCreateGoodsOrder
>;

const cart: CartDto = {
  cartId: 1,
  userId: 3,
  items: [
    {
      id: 11,
      productId: 121,
      productName: '실내 클라이밍 초크백',
      productImageUrl: 'https://cdn.example.com/121.jpg',
      unitPrice: '29000',
      quantity: 1,
      subtotal: '29000',
    },
    {
      id: 12,
      productId: 122,
      productName: '카본 배드민턴 라켓',
      productImageUrl: 'https://cdn.example.com/122.jpg',
      unitPrice: '119000',
      quantity: 2,
      subtotal: '238000',
    },
  ],
  totalAmount: '267000',
};

function mockCartState(data: CartDto | undefined, overrides: { isError?: boolean } = {}) {
  useCartMock.mockReturnValue({
    data,
    isLoading: false,
    isError: overrides.isError ?? false,
    refetch: jest.fn(),
  } as unknown as ReturnType<typeof useCart>);
  const idleMutation = { mutate: jest.fn(), isPending: false };
  useUpdateCartItemMock.mockReturnValue(
    idleMutation as unknown as ReturnType<typeof useUpdateCartItem>
  );
  useRemoveCartItemMock.mockReturnValue(
    idleMutation as unknown as ReturnType<typeof useRemoveCartItem>
  );
  useCreateGoodsOrderMock.mockReturnValue(
    idleMutation as unknown as ReturnType<typeof useCreateGoodsOrder>
  );
}

describe('CartScreen', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('항목을 내부 식별자가 아니라 상품명으로 렌더한다', () => {
    mockCartState(cart);

    render(<CartScreen />);

    expect(screen.getByText('실내 클라이밍 초크백')).toBeTruthy();
    expect(screen.getByText('카본 배드민턴 라켓')).toBeTruthy();
    expect(screen.queryByText('상품 #121')).toBeNull();
    expect(screen.queryByText('상품 #122')).toBeNull();
  });

  it('항목 소계와 장바구니 합계 금액을 렌더한다', () => {
    mockCartState(cart);

    render(<CartScreen />);

    expect(screen.getByText('29,000원')).toBeTruthy();
    expect(screen.getByText('238,000원')).toBeTruthy();
    expect(screen.getByText('총 267,000원')).toBeTruthy();
  });

  it('수량 조절 버튼의 접근성 라벨에 상품명을 쓴다', () => {
    mockCartState(cart);

    render(<CartScreen />);

    expect(screen.getByLabelText('카본 배드민턴 라켓 수량 증가')).toBeTruthy();
    expect(screen.getByLabelText('카본 배드민턴 라켓 삭제')).toBeTruthy();
  });

  it('장바구니가 비면 빈 상태를 보여준다', () => {
    mockCartState({ ...cart, items: [], totalAmount: '0' });

    render(<CartScreen />);

    expect(screen.getByText('장바구니가 비어 있습니다.')).toBeTruthy();
  });

  it('조회 실패 시 오류와 재시도를 보여준다', () => {
    mockCartState(undefined, { isError: true });

    render(<CartScreen />);

    expect(screen.getByText('장바구니를 불러오지 못했습니다.')).toBeTruthy();
    expect(screen.getByLabelText('다시 시도')).toBeTruthy();
  });
});
