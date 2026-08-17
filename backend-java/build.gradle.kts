plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // JWT。api だけ compile 時に見えればよく、実装は実行時に解決される。
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // テストは Docker Compose 上の calorie_test データベースに対して実行する。
    // H2 で代用しないのは、PostgreSQL 固有の CHECK 制約や型の挙動を
    // 本番と同じ条件で検証したいため。
    //
    // Testcontainers は採用していない。Docker Desktop 29 系では docker-java が
    // 名前付きパイプ越しの info 応答を解釈できず、Docker を検出できなかったため
    // （1.20.4 / 1.21.3 の双方で再現）。詳細は docs/00-phase0-setup.html を参照。
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // record パターンなどの可読性のため、パラメータ名をクラスファイルに残す
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("file.encoding", "UTF-8")
}
