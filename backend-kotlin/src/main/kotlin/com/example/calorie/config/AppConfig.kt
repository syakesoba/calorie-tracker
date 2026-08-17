package com.example.calorie.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class AppConfig {

    /**
     * 「今日」の判定に使う時計。
     *
     * Bean にしておくことで、テストで固定の日付に差し替えられる。
     * サービス内で `LocalDate.now()` を直接呼ぶと、日付をまたぐ挙動をテストできなくなる。
     *
     * システム既定のタイムゾーンを使う。このアプリは日本国内の利用を前提としており、
     * 「その日に何を食べたか」は利用者のローカル日付で数えるのが自然なため。
     */
    @Bean
    fun clock(): Clock = Clock.systemDefaultZone()
}
