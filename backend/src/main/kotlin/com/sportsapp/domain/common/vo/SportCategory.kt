package com.sportsapp.domain.common.vo

/**
 * 커뮤니티 개설 시 지정하는 스포츠 종목 카테고리 (TDD FR-1).
 * goods 도메인의 [com.sportsapp.domain.goods.vo.ProductCategory](상품 분류)와는
 * 별개 개념으로, 어떤 종목의 커뮤니티인지를 분류한다. 분류에 없는 종목은 ETC로 지정한다.
 *
 * community 컨텍스트 전용이 아닌 공유 커널(domain.common.vo)에 위치한다 — post 등
 * 다른 도메인 컨텍스트가 도메인 간 교차 import 없이 이 값을 typed로 보유하기 위함
 * (20260707-post-community-연동-tdd.md Possible Solutions B-1 / R1).
 */
enum class SportCategory {
    SOCCER,
    BASKETBALL,
    BASEBALL,
    TENNIS,
    BADMINTON,
    GOLF,
    RUNNING,
    CYCLING,
    SWIMMING,
    HIKING,
    YOGA,
    ETC,
}
