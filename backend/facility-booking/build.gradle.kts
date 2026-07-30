// [W1-01a] facility-booking — facility·booking. facility-booking->payment 8파일(실측). 빈 골격만 선언한다.
// 컨텍스트 소스 이관은 W1-01b 소관.
plugins {
    id("sportsapp.kotlin-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":payment"))
}
