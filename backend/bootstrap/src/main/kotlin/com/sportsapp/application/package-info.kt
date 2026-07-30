/**
 * Application Layer
 *
 * 책임: UseCase 단위 오케스트레이션 (@Transactional 선언 위치).
 *       Command, Response DTO 정의.
 *
 * 의존: domain layer만 허용.
 * 금지: presentation/infrastructure 직접 import, 비즈니스 로직.
 */
package com.sportsapp.application
