package com.sportsapp.presentation.mcp.pii

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZoneId
import java.time.ZonedDateTime

class PiiMaskerTest : BehaviorSpec({

    Given("PiiMasker.name") {
        When("한국어 이름 김민수가 주어지면") {
            Then("[U-01] 첫 글자만 노출되고 나머지는 ** 로 마스킹된다") {
                PiiMasker.name("김민수") shouldBe "김**"
            }
        }
        When("영어 이름 John Smith 가 주어지면") {
            Then("[U-02] 각 토큰의 첫 글자만 노출된다") {
                PiiMasker.name("John Smith") shouldBe "J*** S****"
            }
        }
        When("한 글자 이름이 주어지면") {
            Then("[U-03] 첫 글자 + 최소 1개 *") {
                PiiMasker.name("김") shouldBe "김*"
            }
        }
        When("null/공백이 주어지면") {
            Then("[U-04] 그대로 반환한다") {
                PiiMasker.name(null) shouldBe null
                PiiMasker.name("") shouldBe ""
                PiiMasker.name("   ") shouldBe "   "
            }
        }
    }

    Given("PiiMasker.mobilePhone") {
        When("표준 형식 010-1234-5678 이 주어지면") {
            Then("[U-05] 010-****-5678 로 마스킹된다") {
                PiiMasker.mobilePhone("010-1234-5678") shouldBe "010-****-5678"
            }
        }
        When("하이픈 없는 01012345678 이 주어지면") {
            Then("[U-06] 010-****-5678 로 정규화 + 마스킹된다") {
                PiiMasker.mobilePhone("01012345678") shouldBe "010-****-5678"
            }
        }
        When("형식이 맞지 않는 값이 주어지면") {
            Then("[U-07] REDACTED 로 마스킹된다") {
                PiiMasker.mobilePhone("123") shouldBe PiiMasker.REDACTED
                PiiMasker.mobilePhone("020-1234-5678") shouldBe PiiMasker.REDACTED
            }
        }
    }

    Given("PiiMasker.landlinePhone") {
        When("02-123-4567 이 주어지면") {
            Then("[U-08] 0**-***-4567 로 마스킹된다") {
                PiiMasker.landlinePhone("02-123-4567") shouldBe "0**-***-4567"
            }
        }
    }

    Given("PiiMasker.email") {
        When("john@gmail.com 이 주어지면") {
            Then("[U-09] j***@***.com 으로 마스킹된다") {
                PiiMasker.email("john@gmail.com") shouldBe "j***@***.com"
            }
        }
        When("도메인이 없거나 잘못된 형식") {
            Then("[U-10] REDACTED 반환") {
                PiiMasker.email("@nodomain.com") shouldBe PiiMasker.REDACTED
                PiiMasker.email("noatdomain.com") shouldBe PiiMasker.REDACTED
                PiiMasker.email("local@") shouldBe PiiMasker.REDACTED
            }
        }
    }

    Given("PiiMasker.address") {
        When("서울 강남구 역삼동 123-45 가 주어지면") {
            Then("[U-11] 시·군·구까지만 노출 — 서울 강남구") {
                PiiMasker.address("서울 강남구 역삼동 123-45") shouldBe "서울 강남구"
            }
        }
        When("단일 토큰 주소") {
            Then("[U-12] 그대로 반환") {
                PiiMasker.address("서울") shouldBe "서울"
            }
        }
    }

    Given("PiiMasker.birthdate") {
        val seoul = ZoneId.of("Asia/Seoul")
        val now = ZonedDateTime.of(2026, 5, 21, 0, 0, 0, 0, seoul)
        When("1990-03-15 (36세) 가 주어지면") {
            Then("[U-13] 30대 중반 으로 마스킹된다") {
                val birth = ZonedDateTime.of(1990, 3, 15, 0, 0, 0, 0, seoul)
                PiiMasker.birthdate(birth, now) shouldBe "30대 중반"
            }
        }
        When("2001-06-01 (24세) 가 주어지면") {
            Then("[U-14] 20대 중반 으로 마스킹된다") {
                val birth = ZonedDateTime.of(2001, 6, 1, 0, 0, 0, 0, seoul)
                PiiMasker.birthdate(birth, now) shouldBe "20대 중반"
            }
        }
        When("null 입력") {
            Then("[U-15] null 반환") {
                PiiMasker.birthdate(null, now) shouldBe null
            }
        }
    }

    Given("PiiMasker.cardNumber") {
        When("1234567890123456 (16자리) 가 주어지면") {
            Then("[U-16] **** **** **** 3456 로 마스킹된다") {
                PiiMasker.cardNumber("1234567890123456") shouldBe "**** **** **** 3456"
            }
        }
        When("자릿수 부족") {
            Then("[U-17] REDACTED 반환") {
                PiiMasker.cardNumber("123") shouldBe PiiMasker.REDACTED
            }
        }
    }

    Given("PiiMasker.accountNumber") {
        When("110-123-456789 가 주어지면") {
            Then("[U-18] ***-***-6789 로 마스킹된다") {
                PiiMasker.accountNumber("110-123-456789") shouldBe "***-***-6789"
            }
        }
    }
})
