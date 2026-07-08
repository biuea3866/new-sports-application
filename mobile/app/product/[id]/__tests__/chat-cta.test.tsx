/**
 * ProductDetailScreen — 판매자 채팅하기 CTA
 * 근거: FE-14 티켓 "테스트 케이스", design-fe-app.md S8
 *
 * 기존 화면(useProducts/useAddCartItem/useCurrentUserId)과 useStartGoodsChat을 모킹해
 * 신규 CTA 배선만 검증한다.
 */
import React from 'react';
import { Alert } from 'react-native';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { ProductWithStock } from '../../../../api/goods';
import type { RoomResponse } from '../../../../api/types';
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

const sellerProduct: ProductWithStock = {
  id: 1,
  name: '나이키 한정판 스니커즈',
  category: 'APPAREL',
  price: 129000,
  description: '한정판 스니커즈입니다.',
  imageUrl: 'https://example.com/img.jpg',
  status: 'ACTIVE',
  stockQuantity: 10,
  ownerId: 99,
};

function mockUseProductsReturn(products: ProductWithStock[]) {
  useProductsMock.mockReturnValue({
    data: products,
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
  } as unknown as ReturnType<typeof useProducts>);
}

function pressConfirm() {
  const alertMock = Alert.alert as jest.Mock;
  const lastCall = alertMock.mock.calls[alertMock.mock.calls.length - 1];
  const buttons = lastCall[2] as { text: string; onPress?: () => void }[];
  const confirmButton = buttons.find((button) => button.text === '확인');
  confirmButton?.onPress?.();
}

describe('ProductDetailScreen 판매자 채팅하기 CTA', () => {
  let pushMock: jest.Mock;
  let mutateMock: jest.Mock;

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    pushMock = jest.fn();
    mutateMock = jest.fn();
    // chat.goods.enabled는 기본 OFF이므로, 이 describe의 기존 케이스(ON 상태 동작 검증)는
    // 명시적으로 켠 상태에서 실행한다. OFF 상태 검증은 별도 케이스에서 다룬다.
    process.env.EXPO_PUBLIC_CHAT_GOODS_ENABLED = 'true';

    useLocalSearchParamsMock.mockReturnValue({ id: '1' });
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
      mutate: mutateMock,
      isPending: false,
    } as unknown as ReturnType<typeof useStartGoodsChat>);
    jest.spyOn(Alert, 'alert');
  });

  afterEach(() => {
    jest.clearAllMocks();
    delete process.env.EXPO_PUBLIC_CHAT_GOODS_ENABLED;
  });

  it('chat.goods.enabled 플래그가 기본값(OFF)이면 채팅 CTA가 렌더되지 않는다', () => {
    delete process.env.EXPO_PUBLIC_CHAT_GOODS_ENABLED;
    mockUseProductsReturn([sellerProduct]);

    render(<ProductDetailScreen />);

    expect(screen.queryByLabelText('판매자와 채팅하기')).toBeNull();
  });

  it('chat.goods.enabled 플래그가 ON이면 채팅 CTA가 렌더된다', () => {
    process.env.EXPO_PUBLIC_CHAT_GOODS_ENABLED = 'true';
    mockUseProductsReturn([sellerProduct]);

    render(<ProductDetailScreen />);

    expect(screen.getByLabelText('판매자와 채팅하기')).toBeTruthy();
  });

  it('"채팅하기" 탭 → 확인 시트 → 확정 시 방으로 이동한다', async () => {
    mockUseProductsReturn([sellerProduct]);
    const room: RoomResponse = { id: 42, type: 'DIRECT', name: null };
    mutateMock.mockImplementation((_productId, options) => {
      options.onSuccess(room);
    });

    render(<ProductDetailScreen />);
    fireEvent.press(screen.getByLabelText('판매자와 채팅하기'));

    expect(Alert.alert).toHaveBeenCalledWith(
      '판매자와 채팅',
      '판매자와 1:1 채팅을 시작할까요?',
      expect.any(Array)
    );

    pressConfirm();

    expect(mutateMock).toHaveBeenCalledWith(sellerProduct.id, expect.any(Object));
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith('/rooms/42'));
  });

  it('기존 거래 방이 있으면 같은 roomId로 이동한다(중복 생성 없음)', async () => {
    mockUseProductsReturn([sellerProduct]);
    const existingRoom: RoomResponse = { id: 7, type: 'DIRECT', name: null };
    mutateMock.mockImplementation((_productId, options) => {
      options.onSuccess(existingRoom);
    });

    render(<ProductDetailScreen />);
    fireEvent.press(screen.getByLabelText('판매자와 채팅하기'));
    pressConfirm();

    await waitFor(() => expect(pushMock).toHaveBeenCalledWith('/rooms/7'));
    expect(pushMock).toHaveBeenCalledTimes(1);
  });

  it('본인 상품이면 채팅 CTA가 렌더되지 않는다', () => {
    useCurrentUserIdMock.mockReturnValue(99);
    mockUseProductsReturn([sellerProduct]);

    render(<ProductDetailScreen />);

    expect(screen.queryByLabelText('판매자와 채팅하기')).toBeNull();
  });

  it('방 생성 실패 시 오류 토스트가 표시되고 화면에 머문다', () => {
    mockUseProductsReturn([sellerProduct]);
    mutateMock.mockImplementation((_productId, options) => {
      options.onError(new Error('요청 실패'));
    });

    render(<ProductDetailScreen />);
    fireEvent.press(screen.getByLabelText('판매자와 채팅하기'));
    pressConfirm();

    expect(Alert.alert).toHaveBeenLastCalledWith('오류', '채팅을 시작하지 못했어요.');
    expect(pushMock).not.toHaveBeenCalled();
    expect(screen.getByLabelText('판매자와 채팅하기')).toBeTruthy();
  });

  it('채팅 CTA가 테마 토큰으로 라이트/다크 모두 렌더된다', () => {
    mockUseProductsReturn([sellerProduct]);

    mockUseColorScheme.mockReturnValue('light');
    const { unmount } = render(<ProductDetailScreen />);
    expect(screen.getByLabelText('판매자와 채팅하기')).toBeTruthy();
    unmount();

    mockUseColorScheme.mockReturnValue('dark');
    render(<ProductDetailScreen />);
    expect(screen.getByLabelText('판매자와 채팅하기')).toBeTruthy();
  });
});
