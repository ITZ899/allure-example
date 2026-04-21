plugins {
    java
    id("io.qameta.allure") version "3.0.2"
}

/** Версия Java-адаптеров Allure (JUnit 5, аннотации в тестах). */
val allureAdapterVersion = "2.34.0"

/**
 * Allure commandline для задач Gradle `./gradlew allureReport` / `allureServe` (линейка 2.x на Maven).
 * HTML-отчёт Allure Report 3 собирайте через npm: `npm run allure:generate` или `npx allure …`.
 */
val allureCommandlineVersion = "2.39.0"

group = "io.eroshenkoam"
version = version

allure {
    version.set(allureCommandlineVersion)
    adapter {
        autoconfigure.set(true)
        aspectjWeaver.set(true)
        allureJavaVersion.set(allureAdapterVersion)
        frameworks {
            junit5 {
                adapterVersion.set(allureAdapterVersion)
            }
        }
    }
}

tasks.withType(JavaCompile::class) {
    sourceCompatibility = "${JavaVersion.VERSION_1_8}"
    targetCompatibility = "${JavaVersion.VERSION_1_8}"
    options.encoding = "UTF-8"
}

tasks.withType(Test::class) {
    ignoreFailures = true
    useJUnitPlatform {

    }
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.config.strategy", "dynamic")

    systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
}


repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("commons-io:commons-io:2.6")
    implementation("io.qameta.allure:allure-java-commons:$allureAdapterVersion")
    implementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
    implementation("org.junit.jupiter:junit-jupiter-engine:5.7.2")
    implementation("org.junit.jupiter:junit-jupiter-params:5.7.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
