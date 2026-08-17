package com.example.calorie

import com.example.calorie.config.JwtProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

/**
 * カロリー管理アプリ Kotlin バックエンドの起動クラス。
 *
 * Java 実装（ポート 8080）と同じ PostgreSQL・同じ API 仕様を共有する。
 * ただし **スキーマの所有者は Java 側だけ** であり、こちらは Flyway を持たない。
 * エンティティとスキーマのずれは `ddl-auto=validate` が起動時に検知する。
 */
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class)
class CalorieApplication

fun main(args: Array<String>) {
    runApplication<CalorieApplication>(*args)
}
