// [W1-01a] edge — virtualqueue + catalog·order 파사드 + image + 게이트웨이 라우팅.
// edge->commerce 13 / facility-booking 6 / social 2 (실측). 빈 골격만 선언한다.
// 컨텍스트 소스 이관은 W1-01d 소관.
plugins {
    id("sportsapp.kotlin-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":commerce"))
    implementation(project(":facility-booking"))
    implementation(project(":social"))
}
