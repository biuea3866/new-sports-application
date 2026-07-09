/**
 * order-detail-format — 주문 상세(Option A) 화면이 쓰는 순수 뷰모델 변환 유틸 검증.
 * 각 orderType의 실제 백엔드 응답 계약(부족한 필드 포함)을 공통 뷰모델로 정규화한다.
 */
import {
  formatOrderDetailDateTime,
  toApplicationDetailViewModel,
  toBookingDetailViewModel,
  toGoodsOrderDetailViewModel,
  toTicketOrderDetailViewModel,
} from '../order-detail-format';
import type { ApplicationDetailResponse } from '../../api/recruitment';
import type {
  BookingResponse,
  GoodsOrderDetailResponse,
  TicketOrderResponse,
} from '../../api/types';

describe('formatOrderDetailDateTime', () => {
  it('유효한 ISO 시각을 로컬 문자열로 변환한다', () => {
    expect(formatOrderDetailDateTime('2026-07-05T10:00:00.000Z')).not.toBe('정보 없음');
  });

  it('null이면 "정보 없음"을 반환한다', () => {
    expect(formatOrderDetailDateTime(null)).toBe('정보 없음');
  });

  it('빈 문자열이면 "정보 없음"을 반환한다', () => {
    expect(formatOrderDetailDateTime('')).toBe('정보 없음');
  });
});

describe('toBookingDetailViewModel', () => {
  const booking: BookingResponse = {
    id: 42,
    slotId: 7,
    userId: 1,
    status: 'CONFIRMED',
    paymentId: 900,
    paymentStatus: 'PAID',
    createdAt: '2026-07-05T10:00:00.000Z',
    updatedAt: '2026-07-05T10:00:00.000Z',
  };

  it('제목은 "예약 #id" fallback을 쓴다(BookingResponse에 title 없음)', () => {
    expect(toBookingDetailViewModel(42, booking).title).toBe('예약 #42');
  });

  it('상태·결제·일시를 응답에서 정확히 매핑한다', () => {
    const viewModel = toBookingDetailViewModel(42, booking);
    expect(viewModel.statusLabel).toBe('결제완료');
    expect(viewModel.isPaymentConfirmed).toBe(true);
    expect(viewModel.paymentLabel).toBe('결제 #900');
    expect(viewModel.dateTimeLabel).not.toBe('정보 없음');
  });

  it('facilityId가 응답에 없어 원본 보기 경로가 없다(null)', () => {
    expect(toBookingDetailViewModel(42, booking).originRoute).toBeNull();
  });

  it('요약에 슬롯 정보를 포함한다', () => {
    expect(toBookingDetailViewModel(42, booking).summaryLines).toContain('슬롯 #7');
  });
});

describe('toGoodsOrderDetailViewModel', () => {
  it('상품 1건이면 원본 보기가 해당 상품 상세로 연결된다', () => {
    const order: GoodsOrderDetailResponse = {
      id: 5,
      userId: 1,
      status: 'PAID',
      totalAmount: '10000',
      paymentId: 300,
      paymentStatus: 'PAID',
      items: [{ id: 1, productId: 88, quantity: 1, unitPrice: '10000', subtotal: '10000' }],
    };

    const viewModel = toGoodsOrderDetailViewModel(5, order);

    expect(viewModel.originRoute).toBe('/product/88');
    expect(viewModel.title).toBe('상품 #5');
    expect(viewModel.statusLabel).toBe('결제완료');
    expect(viewModel.isPaymentConfirmed).toBe(true);
  });

  it('상품이 여러 건이면 원본 보기를 제공하지 않는다(단일 상품 매핑 불가)', () => {
    const order: GoodsOrderDetailResponse = {
      id: 6,
      userId: 1,
      status: 'PENDING',
      totalAmount: '20000',
      paymentId: null,
      paymentStatus: null,
      items: [
        { id: 1, productId: 88, quantity: 1, unitPrice: '10000', subtotal: '10000' },
        { id: 2, productId: 89, quantity: 1, unitPrice: '10000', subtotal: '10000' },
      ],
    };

    const viewModel = toGoodsOrderDetailViewModel(6, order);

    expect(viewModel.originRoute).toBeNull();
    expect(viewModel.summaryLines).toContain('합계 20,000원');
  });

  it('status가 null이면 "상태 미상"으로 표시한다', () => {
    const order: GoodsOrderDetailResponse = {
      id: 7,
      userId: null,
      status: null,
      totalAmount: '0',
      paymentId: null,
      paymentStatus: null,
      items: [],
    };

    expect(toGoodsOrderDetailViewModel(7, order).statusLabel).toBe('상태 미상');
  });

  it('createdAt이 응답에 없어 주문일시는 항상 "정보 없음"이다', () => {
    const order: GoodsOrderDetailResponse = {
      id: 8,
      userId: 1,
      status: 'DELIVERED',
      totalAmount: '5000',
      paymentId: 1,
      paymentStatus: 'PAID',
      items: [],
    };

    expect(toGoodsOrderDetailViewModel(8, order).dateTimeLabel).toBe('정보 없음');
  });
});

describe('toTicketOrderDetailViewModel', () => {
  const ticketOrder: TicketOrderResponse = {
    ticketOrderId: 12,
    status: 'CONFIRMED',
  };

  it('제목은 "티켓 #ticketOrderId" fallback을 쓴다', () => {
    expect(toTicketOrderDetailViewModel(ticketOrder).title).toBe('티켓 #12');
  });

  it('eventId가 응답에 없어 원본 보기 경로가 없다(null)', () => {
    expect(toTicketOrderDetailViewModel(ticketOrder).originRoute).toBeNull();
  });

  it('paymentId가 응답에 없어 결제 정보는 "정보 없음" 계열 라벨을 쓴다(미결제 오인 방지)', () => {
    expect(toTicketOrderDetailViewModel(ticketOrder).paymentLabel).not.toBe('미결제');
  });
});

describe('toApplicationDetailViewModel', () => {
  const application: ApplicationDetailResponse = {
    applicationId: 100,
    recruitmentId: 9,
    title: '주말 축구 3명 모집',
    status: 'CONFIRMED',
    feeAmount: 5000,
    paymentId: 200,
    createdAt: '2026-07-08T00:00:00+09:00',
  };

  it('제목은 모집명(title)을 그대로 쓴다', () => {
    expect(toApplicationDetailViewModel(application).title).toBe('주말 축구 3명 모집');
  });

  it('원본 보기는 모집 상세로 연결된다', () => {
    expect(toApplicationDetailViewModel(application).originRoute).toBe('/recruitments/9');
  });

  it('feeAmount가 0이거나 null이면 "무료 참가"를 요약에 포함한다', () => {
    const free = toApplicationDetailViewModel({ ...application, feeAmount: null });
    expect(free.summaryLines).toContain('무료 참가');
  });

  it('feeAmount가 있으면 금액을 요약에 포함한다', () => {
    expect(toApplicationDetailViewModel(application).summaryLines).toContain('참가비 5,000원');
  });
});
