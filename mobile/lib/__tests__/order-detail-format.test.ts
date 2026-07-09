/**
 * order-detail-format — 주문 상세(Option A+) 화면이 쓰는 순수 뷰모델 변환 유틸 검증.
 * BE 4종 주문상세 응답 보강(Option A+) 이후의 리치 계약을 기준으로 검증한다.
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
  TicketOrderDetailResponse,
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
    facilityId: '9',
    userId: 1,
    status: 'CONFIRMED',
    paymentId: 900,
    paymentStatus: 'COMPLETED',
    title: '강남 풋살장 예약',
    createdAt: '2026-07-05T10:00:00.000Z',
    updatedAt: '2026-07-05T10:00:00.000Z',
  };

  it('title을 그대로 쓴다(리치 계약 — fallback 불필요)', () => {
    expect(toBookingDetailViewModel(42, booking).title).toBe('강남 풋살장 예약');
  });

  it('title이 null이면 "예약 #id" fallback을 쓴다', () => {
    const withoutTitle: BookingResponse = { ...booking, title: null };
    expect(toBookingDetailViewModel(42, withoutTitle).title).toBe('예약 #42');
  });

  it('상태·결제·일시를 응답에서 정확히 매핑한다', () => {
    const viewModel = toBookingDetailViewModel(42, booking);
    expect(viewModel.statusLabel).toBe('결제완료');
    expect(viewModel.isPaymentConfirmed).toBe(true);
    expect(viewModel.paymentLabel).toBe('결제 #900');
    expect(viewModel.dateTimeLabel).not.toBe('정보 없음');
  });

  it('facilityId가 있으면 원본 보기가 시설 상세로 연결된다', () => {
    expect(toBookingDetailViewModel(42, booking).originRoute).toBe('/facility/9');
  });

  it('facilityId가 null이면 원본 보기 경로가 없다(cancel 경로 등)', () => {
    const withoutFacility: BookingResponse = { ...booking, facilityId: null };
    expect(toBookingDetailViewModel(42, withoutFacility).originRoute).toBeNull();
  });

  it('요약에 슬롯 정보를 포함한다', () => {
    expect(toBookingDetailViewModel(42, booking).summaryLines).toContain('슬롯 #7');
  });
});

describe('toGoodsOrderDetailViewModel', () => {
  const baseOrder: GoodsOrderDetailResponse = {
    id: 5,
    userId: 1,
    status: 'CONFIRMED',
    totalAmount: '10000',
    paymentId: 300,
    paymentStatus: 'COMPLETED',
    title: '요가매트 프리미엄',
    createdAt: '2026-07-05T10:00:00.000Z',
    items: [{ id: 1, productId: 88, quantity: 1, unitPrice: '10000', subtotal: '10000' }],
  };

  it('title을 그대로 쓴다(리치 계약 — fallback 불필요)', () => {
    expect(toGoodsOrderDetailViewModel(5, baseOrder).title).toBe('요가매트 프리미엄');
  });

  it('title이 null이면 "상품 #id" fallback을 쓴다', () => {
    const withoutTitle: GoodsOrderDetailResponse = { ...baseOrder, title: null };
    expect(toGoodsOrderDetailViewModel(5, withoutTitle).title).toBe('상품 #5');
  });

  it('상품 1건이면 원본 보기가 해당 상품 상세로 연결된다', () => {
    const viewModel = toGoodsOrderDetailViewModel(5, baseOrder);

    expect(viewModel.originRoute).toBe('/product/88');
    expect(viewModel.statusLabel).toBe('결제완료');
    expect(viewModel.isPaymentConfirmed).toBe(true);
  });

  it('상품이 여러 건이면 원본 보기를 제공하지 않는다(단일 상품 매핑 불가)', () => {
    const order: GoodsOrderDetailResponse = {
      ...baseOrder,
      id: 6,
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
    const order: GoodsOrderDetailResponse = { ...baseOrder, id: 7, status: null, items: [] };

    expect(toGoodsOrderDetailViewModel(7, order).statusLabel).toBe('상태 미상');
  });

  it('createdAt을 응답에서 그대로 반영한다(리치 계약)', () => {
    expect(toGoodsOrderDetailViewModel(5, baseOrder).dateTimeLabel).not.toBe('정보 없음');
  });

  it('createdAt이 null이면 "정보 없음"이다', () => {
    const withoutCreatedAt: GoodsOrderDetailResponse = { ...baseOrder, createdAt: null };
    expect(toGoodsOrderDetailViewModel(5, withoutCreatedAt).dateTimeLabel).toBe('정보 없음');
  });
});

describe('toTicketOrderDetailViewModel', () => {
  const ticketOrder: TicketOrderDetailResponse = {
    ticketOrderId: 12,
    status: 'CONFIRMED',
    eventId: 77,
    eventTitle: '2026 서울 마라톤',
    paymentId: 500,
    createdAt: '2026-07-05T10:00:00.000Z',
  };

  it('eventTitle을 제목으로 쓴다(리치 계약)', () => {
    expect(toTicketOrderDetailViewModel(ticketOrder).title).toBe('2026 서울 마라톤');
  });

  it('eventId로 원본(이벤트) 상세 경로가 연결된다', () => {
    expect(toTicketOrderDetailViewModel(ticketOrder).originRoute).toBe('/event/77');
  });

  it('paymentId를 응답에서 그대로 반영한 결제 라벨을 쓴다', () => {
    expect(toTicketOrderDetailViewModel(ticketOrder).paymentLabel).toBe('결제 #500');
  });

  it('paymentId가 null이면 "미결제"로 표시한다', () => {
    const unpaid: TicketOrderDetailResponse = { ...ticketOrder, paymentId: null };
    expect(toTicketOrderDetailViewModel(unpaid).paymentLabel).toBe('미결제');
  });

  it('createdAt을 응답에서 그대로 반영한다(리치 계약)', () => {
    expect(toTicketOrderDetailViewModel(ticketOrder).dateTimeLabel).not.toBe('정보 없음');
  });
});

describe('toApplicationDetailViewModel', () => {
  const application: ApplicationDetailResponse = {
    applicationId: 100,
    recruitmentId: 9,
    recruitmentTitle: '주말 축구 3명 모집',
    status: 'CONFIRMED',
    feeAmount: 5000,
    paymentId: 200,
    createdAt: '2026-07-08T00:00:00+09:00',
  };

  it('제목은 모집명(recruitmentTitle)을 그대로 쓴다', () => {
    expect(toApplicationDetailViewModel(application).title).toBe('주말 축구 3명 모집');
  });

  it('원본 보기는 모집 상세로 연결된다', () => {
    expect(toApplicationDetailViewModel(application).originRoute).toBe('/recruitments/9');
  });

  it('feeAmount가 0이면 "무료 참가"를 요약에 포함한다', () => {
    const free = toApplicationDetailViewModel({ ...application, feeAmount: 0 });
    expect(free.summaryLines).toContain('무료 참가');
  });

  it('feeAmount가 있으면 금액을 요약에 포함한다', () => {
    expect(toApplicationDetailViewModel(application).summaryLines).toContain('참가비 5,000원');
  });
});
