/**
 * recruitment-cancellation — 신청 취소 단계 수수료 미리보기 순수 계산 검증.
 * 근거: design-fe-app.md Terminology "단계 수수료"(7일 초과 0% / 3~7일 5% / 3일 이내 10%),
 * A-R6 와이어프레임("환불 예정 4,750원 · 수수료 5%(250원) 공제 · 마감까지 5일 남음",
 * "마감 후 진입 시 취소할 수 없어요", "무료 구간(7일 초과) 전액 환불").
 *
 * 수수료율 SSOT는 BE CancellationPolicy — 이 계산은 표시용 미리보기이며, 실제 공제는
 * 서버 확정값을 사용한다(design-fe-app.md "상태관리 설계").
 */
import { calculateCancellationPreview } from '../recruitment-cancellation';

const NOW = new Date('2026-07-08T00:00:00+09:00');

describe('calculateCancellationPreview', () => {
  it('마감까지 5일 남으면 5% 수수료를 공제한 환불액을 반환한다', () => {
    const preview = calculateCancellationPreview(5000, '2026-07-13T00:00:00+09:00', NOW);

    expect(preview.tier).toBe('STANDARD');
    expect(preview.feeRate).toBe(0.05);
    expect(preview.deductedAmount).toBe(250);
    expect(preview.refundAmount).toBe(4750);
    expect(preview.isCancellable).toBe(true);
    expect(preview.daysRemaining).toBe(5);
  });

  it('마감까지 7일 초과로 남으면 수수료 없이 전액 환불이다', () => {
    const preview = calculateCancellationPreview(5000, '2026-07-20T00:00:00+09:00', NOW);

    expect(preview.tier).toBe('FREE');
    expect(preview.feeRate).toBe(0);
    expect(preview.deductedAmount).toBe(0);
    expect(preview.refundAmount).toBe(5000);
  });

  it('마감까지 3일 미만이면 10% 수수료를 공제한다', () => {
    const preview = calculateCancellationPreview(5000, '2026-07-09T12:00:00+09:00', NOW);

    expect(preview.tier).toBe('LATE');
    expect(preview.feeRate).toBe(0.1);
    expect(preview.deductedAmount).toBe(500);
    expect(preview.refundAmount).toBe(4500);
  });

  it('마감이 이미 지났으면 취소할 수 없다', () => {
    const preview = calculateCancellationPreview(5000, '2026-07-01T00:00:00+09:00', NOW);

    expect(preview.tier).toBe('CLOSED');
    expect(preview.isCancellable).toBe(false);
    expect(preview.refundAmount).toBe(0);
  });

  it('참가비가 0원(무료 모집)이면 환불액도 0원이다', () => {
    const preview = calculateCancellationPreview(0, '2026-07-20T00:00:00+09:00', NOW);

    expect(preview.tier).toBe('FREE');
    expect(preview.refundAmount).toBe(0);
    expect(preview.isCancellable).toBe(true);
  });
});
