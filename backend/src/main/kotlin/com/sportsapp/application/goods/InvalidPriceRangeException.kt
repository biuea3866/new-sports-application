package com.sportsapp.application.goods

import java.math.BigDecimal

class InvalidPriceRangeException(priceMin: BigDecimal, priceMax: BigDecimal) :
    IllegalArgumentException("priceMin($priceMin)이 priceMax($priceMax)보다 클 수 없습니다.")
