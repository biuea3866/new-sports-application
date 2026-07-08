package com.sportsapp.domain.community.vo

/**
 * 커뮤니티 멤버의 역할 (TDD Detail Design "CommunityMember").
 * HOST는 개설자·위임받은 방장, MEMBER는 일반 멤버다.
 */
enum class CommunityRole {
    HOST,
    MEMBER,
}
