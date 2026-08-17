plugins {
    kotlin("jvm") version "2.1.20"

    // Kotlin のクラスは既定で final だが、Spring は AOP プロキシのためにサブクラス化する。
    // このプラグインが @Component / @Configuration 等を自動で open にする。
    // Java 側には対応するものが要らない、言語差の実例のひとつ。
    kotlin("plugin.spring") version "2.1.20"

    // JPA はリフレクションで空のインスタンスを作るため、引数なしコンストラクタが必要。
    // @Entity に対して合成のデフォルトコンストラクタを生成させる。
    kotlin("plugin.jpa") version "2.1.20"

    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    // ---- Java 実装（backend-java）と同一の依存構成にすること ----
    // 片方だけライブラリを増やすと、コードの差が「言語の差」なのか
    // 「ライブラリの差」なのか判別できなくなり、比較が成立しない。
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kotlin の data class / null 許容型を Jackson が正しく扱えるようにする。
    // これも Java 側には不要な、フレームワーク適合のための追加。
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    runtimeOnly("org.postgresql:postgresql")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Flyway は依存に入れない。スキーマの所有者は Java 側だけ。
    // 依存に入れておくと、設定ミスで Kotlin 側がマイグレーションを走らせうる。

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // Kotest は使わず JUnit 5 に揃える。テスト基盤を変えると、テストコードの差が
    // 言語の差なのかテストライブラリの差なのか分からなくなる。
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("file.encoding", "UTF-8")
}
