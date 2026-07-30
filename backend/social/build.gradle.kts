// [W1-01a] social — community·post·message·recruitment. social->payment 4 / commerce 1 /
// facility-booking 1 / platform 1(GuestExpiryScheduler, 실측). 빈 골격만 선언한다.
// 컨텍스트 소스 이관은 W1-01d 소관.
plugins {
    id("sportsapp.kotlin-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":payment"))
    implementation(project(":commerce"))
    implementation(project(":facility-booking"))
    implementation(project(":platform"))
}
