package com.sportsapp.domain.user.entity

import com.sportsapp.domain.user.exception.InvalidNicknameException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 닉네임 등록·수정·표시 이름 규칙. 소셜 화면(게시글 작성자·방장·초대자·신청자)이 내부 식별자
 * 대신 사람이 읽는 이름을 노출하기 위한 도메인 규칙이며, 검증은 전부 Entity 캡슐화 메서드가 갖는다.
 */
class UserNicknameTest : BehaviorSpec({

    Given("정상 닉네임") {
        When("User.create 로 가입하면") {
            val user = User.create("player@example.com", "hash", " 김철수 ")

            Then("앞뒤 공백을 제거한 값이 닉네임으로 저장된다") {
                user.nickname shouldBe "김철수"
            }

            Then("표시 이름은 닉네임을 그대로 사용한다") {
                user.displayName shouldBe "김철수"
            }
        }

        When("영문·숫자·밑줄 조합으로 가입하면") {
            Then("허용된다") {
                shouldNotThrowAny {
                    User.create("player2@example.com", "hash", "coach_kim2")
                }
            }
        }
    }

    Given("길이 경계값 닉네임") {
        When("2자로 가입하면") {
            Then("허용된다") {
                User.create("min@example.com", "hash", "가나").nickname shouldBe "가나"
            }
        }

        When("20자로 가입하면") {
            val twentyCharacters = "a".repeat(20)
            Then("허용된다") {
                User.create("max@example.com", "hash", twentyCharacters).nickname shouldBe twentyCharacters
            }
        }

        When("1자로 가입하면") {
            Then("InvalidNicknameException 을 던진다") {
                shouldThrow<InvalidNicknameException> {
                    User.create("short@example.com", "hash", "가")
                }
            }
        }

        When("21자로 가입하면") {
            Then("InvalidNicknameException 을 던진다") {
                shouldThrow<InvalidNicknameException> {
                    User.create("long@example.com", "hash", "a".repeat(21))
                }
            }
        }
    }

    Given("허용되지 않는 닉네임") {
        When("공백만 입력하면") {
            Then("InvalidNicknameException 을 던진다") {
                shouldThrow<InvalidNicknameException> {
                    User.create("blank@example.com", "hash", "   ")
                }
            }
        }

        When("중간에 공백이 섞이면") {
            Then("InvalidNicknameException 을 던진다") {
                shouldThrow<InvalidNicknameException> {
                    User.create("space@example.com", "hash", "김 철수")
                }
            }
        }

        When("특수문자가 섞이면") {
            Then("InvalidNicknameException 을 던진다") {
                shouldThrow<InvalidNicknameException> {
                    User.create("symbol@example.com", "hash", "김철수!")
                }
            }
        }
    }

    Given("가입된 사용자") {
        When("changeNickname 으로 수정하면") {
            val user = User.create("edit@example.com", "hash", "이전닉네임")
            user.changeNickname("새로운닉네임")

            Then("닉네임이 교체된다") {
                user.nickname shouldBe "새로운닉네임"
            }
        }

        When("changeNickname 에 잘못된 값을 주면") {
            val user = User.create("edit2@example.com", "hash", "이전닉네임")

            Then("InvalidNicknameException 을 던지고 기존 닉네임을 유지한다") {
                shouldThrow<InvalidNicknameException> { user.changeNickname("x") }
                user.nickname shouldBe "이전닉네임"
            }
        }
    }

    Given("닉네임이 없는 기존 사용자") {
        val user = User(
            email = "legacy@example.com",
            passwordHash = "hash",
            status = UserStatus.ACTIVE,
        )

        When("표시 이름을 읽으면") {
            Then("내부 식별자·이메일이 아닌 중립 기본값을 반환한다") {
                user.displayName shouldBe User.UNSET_NICKNAME_DISPLAY_NAME
                user.displayName.contains("legacy") shouldBe false
                user.displayName.contains(user.id.toString()) shouldBe false
            }
        }
    }

    Given("연동 대리 계정") {
        When("createInactive 로 생성하면") {
            val user = User.createInactive("partner@example.com", "hash")

            Then("닉네임 없이 생성되고 표시 이름은 기본값이다") {
                user.nickname shouldBe null
                user.displayName shouldBe User.UNSET_NICKNAME_DISPLAY_NAME
            }
        }
    }
})
