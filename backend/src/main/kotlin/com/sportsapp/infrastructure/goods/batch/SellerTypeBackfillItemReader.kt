package com.sportsapp.infrastructure.goods.batch

import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import org.springframework.batch.item.ExecutionContext
import org.springframework.batch.item.ItemStreamReader
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

/**
 * BE-11 청크 리더 — OFFSET을 전진시키는 페이징(JpaPagingItemReader) 대신, 버퍼가 비면
 * "WHERE seller_type IS NULL ORDER BY id LIMIT chunkSize"를 오프셋 0으로 다시 조회한다.
 *
 * 이유 1(OFFSET 페이징 금지): 이 잡은 읽은 행을 청크 커밋 시점에 곧바로 WHERE 조건에서
 * 빠지게 만든다(writer가 seller_type을 채움). OFFSET 기반 페이징을 쓰면 청크 커밋 사이에
 * 결과 집합이 줄어들어(shifting window) 다음 페이지의 OFFSET이 아직 처리하지 않은 행을
 * 건너뛴다 — 실측 회귀 확인(7건/청크3 실행 시 4건만 처리, 3건 누락, remaining=3으로 검증 실패).
 *
 * 이유 2(짧은 페이지 = 소진 표시, 재조회 금지): 청크 하나를 채우려면 Step이 `read()`를
 * chunkSize번(또는 null 반환까지) 반복 호출한다. 매번 무조건 재조회하면, 한 청크 안에서
 * "아직 커밋 전이라 여전히 NULL인" 마지막 몇 건을 재조회로 중복 반환한다(실측 회귀 확인:
 * 7건/청크3 → writeCount=9, 2건/청크3 → writeCount=4). 조회 결과가 chunkSize보다 짧으면
 * "현재 커밋 안 된 시점 기준 더 이상 없다"는 뜻이므로 그 페이지를 다 쓴 뒤로는 재조회하지
 * 않고 null(스텝 종료 신호)을 반환한다.
 *
 * [ItemStreamReader] 구현: 이 리더는 싱글턴 빈으로 여러 Job 실행에서 재사용된다(스텝 스코프
 * 미사용). `exhausted`/`buffer`는 실행 간 공유되면 안 되므로 `open()`에서 매 스텝 시작마다
 * 초기화한다 — Step이 스트림 라이프사이클(open→...→close)을 보장하므로 재실행 시에도
 * 안전하다(AbstractPagingItemReader와 동일한 open/close 리셋 패턴).
 */
class SellerTypeBackfillItemReader(
    private val productJpaRepository: ProductJpaRepository,
    private val chunkSize: Int,
) : ItemStreamReader<Product> {

    private var buffer: Iterator<Product> = emptyList<Product>().iterator()
    private var exhausted = false

    override fun read(): Product? {
        if (buffer.hasNext()) return buffer.next()
        if (exhausted) return null
        val page = productJpaRepository.findBySellerTypeIsNull(
            PageRequest.of(0, chunkSize, Sort.by(Sort.Direction.ASC, "id")),
        )
        if (page.size < chunkSize) exhausted = true
        buffer = page.iterator()
        return if (buffer.hasNext()) buffer.next() else null
    }

    override fun open(executionContext: ExecutionContext) {
        buffer = emptyList<Product>().iterator()
        exhausted = false
    }

    override fun update(executionContext: ExecutionContext) = Unit

    override fun close() {
        buffer = emptyList<Product>().iterator()
        exhausted = false
    }
}
