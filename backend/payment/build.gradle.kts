// [W1-01a] payment — 아웃바운드 동기 의존 0 (§1-2 실측). 이 티켓에서는 빈 골격만 선언한다.
// 컨텍스트 소스 이관은 W1-01b 소관.
plugins {
    id("sportsapp.kotlin-conventions")
}

dependencies {
    implementation(project(":common"))
}
