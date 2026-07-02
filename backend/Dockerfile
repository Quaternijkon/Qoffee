FROM gradle:8.14.3-jdk17 AS build
WORKDIR /workspace
COPY . .
RUN sed -i '/^org.gradle.java.home=/d; /^org.gradle.java.installations.paths=/d' gradle.properties && \
    rm -f gradle/gradle-daemon-jvm.properties && \
    cat > settings.gradle.kts <<'EOF'
pluginManagement {
    repositories {
        maven(url = "https://maven.aliyun.com/repository/public")
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        maven(url = "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://maven.aliyun.com/repository/public")
        maven(url = "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        mavenCentral()
    }
}

rootProject.name = "QoffeeBackend"
include(":backend")
EOF
RUN gradle --no-daemon :backend:installDist

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/backend/build/install/backend /app
EXPOSE 8080
ENTRYPOINT ["/app/bin/backend"]
