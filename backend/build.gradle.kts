plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.qoffee.backend.MainKt")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hikari)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.bcrypt)
    implementation(libs.logback.classic)

    testImplementation(libs.junit4)
    testImplementation(libs.google.truth)
}
