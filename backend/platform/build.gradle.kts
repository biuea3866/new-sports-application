// [W1-01a] platform — user·partner·notification·alerting·operator·featureflag·mcp·airquality·weather·
// featuredemo·dashboard. platform->{commerce,facility-booking} 12파일(MCP tool + NotificationEventWorker,
// §11-1 W1-01 근거 각주 — R3 미스캔 결합, 1단계는 모듈 의존으로 허용). 빈 골격만 선언한다.
// 컨텍스트 소스 이관은 W1-01c 소관.
plugins {
    id("sportsapp.kotlin-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":payment"))
    implementation(project(":commerce"))
    implementation(project(":facility-booking"))
}
