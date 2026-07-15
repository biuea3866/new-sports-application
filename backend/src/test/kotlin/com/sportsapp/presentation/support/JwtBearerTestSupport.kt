package com.sportsapp.presentation.support

import com.sportsapp.domain.user.gateway.JwtIssuer

/**
 * AUTH-04 — 전체 Spring 컨텍스트(`@AutoConfigureMockMvc`/`TestRestTemplate`) 통합 테스트에서
 * `X-User-Id` 헤더 대신 실 JWT(Authorization: Bearer)로 인증하기 위한 지원 함수.
 *
 * `JwtIssuer`는 상태를 갖지 않는 서명·검증기라(`JwtTokenProvider`) 실제 `users` 테이블에 사용자를
 * 등록하지 않고도 임의 `userId`에 대한 유효 토큰을 발급할 수 있다. 기존 테스트 픽스처가
 * 이미 사용 중인 리터럴 userId(900L, 1L 등)를 그대로 유지하기 위해 등록·로그인 왕복 대신
 * 이 방식을 쓴다.
 */
fun JwtIssuer.bearerTokenFor(
    userId: Long,
    email: String = "test-$userId@sportsapp.local",
    roles: List<String> = listOf("USER"),
): String = "Bearer ${generateAccessToken(userId, email, roles)}"
