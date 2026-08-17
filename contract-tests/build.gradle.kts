plugins {
    java
}

// =============================================================================
// 契約テスト
//
// Java 実装と Kotlin 実装の **両方に同じ検証を流し**、外から見て同一に振る舞うことを
// 確認するためのプロジェクト。
//
// 【設計上の決めごと】
// どちらの実装のコードにも依存しない。HTTP クライアントで外から叩くだけにする。
// 片方のクラスを import すると、その実装に引きずられた検証になってしまうため、
// Spring も両プロジェクトも依存に入れていない。
//
// テストコード自体は Java で書いているが、これは中立性の主張ではなく、
// 依存を最小にするための都合（Kotlin プラグインを足さずに済む）である。
// =============================================================================

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.0")
    // JSON の読み取りのみ。HTTP は JDK 標準の java.net.http を使う。
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("file.encoding", "UTF-8")

    // 検証対象のベース URL。`name=url` をカンマ区切りで渡す。
    //
    //   ./gradlew test -Pbackends="java=http://localhost:8085/api,kotlin=http://localhost:8081/api"
    //
    // 既定は Java 8080 / Kotlin 8081。ポートが埋まっている環境では上書きする。
    val backends = (project.findProperty("backends") as String?)
        ?: "java=http://localhost:8080/api,kotlin=http://localhost:8081/api"
    systemProperty("contract.backends", backends)

    testLogging {
        events("passed", "failed")
        showStandardStreams = false
    }
}
