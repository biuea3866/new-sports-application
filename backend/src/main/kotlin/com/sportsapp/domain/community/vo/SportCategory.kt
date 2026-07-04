package com.sportsapp.domain.community.vo

/**
 * 커뮤니티 개설 시 지정하는 스포츠 종목 카테고리 (TDD FR-1).
 * goods 도메인의 [com.sportsapp.domain.goods.vo.ProductCategory](상품 분류)와는
 * 별개 개념으로, 어떤 종목의 커뮤니티인지를 분류한다. 분류에 없는 종목은 ETC로 지정한다.
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
