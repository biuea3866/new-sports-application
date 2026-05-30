# V25 마이그레이션 롤백 절차

`backend/src/main/resources/db/migration/V25__alter_tickets_ticket_order_id_nullable.sql` 의 롤백 절차.

## 배경

- **마이그레이션**: V25 = `tickets.ticket_order_id` 컬럼을 `BIGINT NOT NULL` → `BIGINT NULL` 전환 + sentinel 0L → NULL backfill
- **티켓**: v1.1 T06 / FR-04 / Open Issue #4
- **배포 순서**: 단계 0 (사전 검증) → 단계 1 (V25 적용) → 단계 2 (T07 `Ticket.kt` Long? 전환 코드 배포)

## 롤백 가능 상태

| 시점 | 롤백 가능 | 비고 |
|---|---|---|
| 단계 0 (사전 검증) 후 | ✅ V25 미적용이므로 롤백 불필요 | — |
| **단계 1 (V25 적용) 후 + 단계 2 (코드 배포) 전** | ✅ 가능 | 본 문서 §2 절차 |
| 단계 2 (코드 배포) 후 | ❌ **forward-only** | NULL row 는 0L sentinel 로 되돌릴 수 없음 (재생성). soft-delete 또는 별도 정책 결정 필요 |

## §1. 단계 1 적용 후 / 단계 2 배포 전 롤백 SQL

```sql
-- 1) backfill 역전: 모든 NULL row 를 0L sentinel 로 되돌림
UPDATE tickets
SET    ticket_order_id = 0
WHERE  ticket_order_id IS NULL;

-- 2) 컬럼을 NOT NULL 로 다시 전환
ALTER TABLE tickets
    MODIFY COLUMN ticket_order_id BIGINT NOT NULL
        COMMENT '티켓 주문 ID. complimentary 티켓은 0L sentinel (v1.0 호환)';

-- 3) Flyway 메타 정정 (필요 시 — DBA 검토 후)
-- DELETE FROM flyway_schema_history WHERE version = '25';
```

**주의**:
- 1) 단계는 `ticket_order_id IS NULL` row 전체를 sentinel 로 만들기 때문에 **단계 2 코드가 일부 배포된 상태에서 실행하면 v1.1 신규 complimentary 발급 row도 sentinel 로 되돌아갑니다** — 데이터 의미 손실. 단계 2 배포 후 롤백은 금지.
- 3) Flyway history 삭제는 V25 재적용 시 필요. **DBA 검토 후 수동 실행**.

## §2. 단계 2 배포 후 사고 대응 (forward-only)

단계 2 코드가 prod 배포된 후 V25 마이그레이션을 되돌려야 하는 사고가 발생하면:

1. **즉시 단계 2 코드 rollback** (이전 버전 redeploy) — `ticket_order_id: Long?` → `ticket_order_id: Long` 되돌림 단 NULL row 에 대한 NPE 발생 가능 → **신규 complimentary 발급 전체 차단**
2. NULL row 식별:
   ```sql
   SELECT id, seat_id, created_at, deleted_at
   FROM   tickets
   WHERE  ticket_order_id IS NULL
   ORDER BY created_at DESC;
   ```
3. NULL row 처리 정책 결정 (PM + Legal):
   - (A) soft-delete (`deleted_at = NOW()`) — 무료 증정 이력 보존, 사용자 미고지
   - (B) 별도 sentinel order 생성 후 `UPDATE ... SET ticket_order_id = <fallback>` — 데이터 정합성 우선
   - (C) hard-delete + 사용자 통지 — Legal 검토 필요
4. 결정 후 §1 절차 ALTER 부분만 실행 (UPDATE 는 정책 결정 결과 적용)

## 검증

롤백 적용 후:

```sql
-- 컬럼 nullable 여부
SELECT IS_NULLABLE
FROM   information_schema.columns
WHERE  table_name = 'tickets' AND column_name = 'ticket_order_id';
-- → 'NO' 반환 확인

-- NULL row 잔존 확인
SELECT COUNT(*) FROM tickets WHERE ticket_order_id IS NULL;
-- → 0 반환 확인 (§1) / 또는 §2 정책 결정에 따른 잔존
```

## 참고

- TPM 산출물: `.analysis/outputs/20260523_mcp-server-v1.1/tpm-analysis.md` T06
- PRD: `.analysis/outputs/20260523_mcp-server-v1.1/prd.md` FR-04
- 단계 0 SELECT SQL + expected 산정: V25 SQL 파일 헤더 주석
