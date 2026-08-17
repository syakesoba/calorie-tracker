package com.example.calorie.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    /**
     * 「今日」の判定に使う時計。
     *
     * <p>Bean にしておくことで、テストで固定の日付に差し替えられる。
     * サービス内で {@code LocalDate.now()} を直接呼ぶと、日付をまたぐ挙動を
     * テストできなくなる。
     *
     * <p>システム既定のタイムゾーンを使う。このアプリは日本国内の利用を
     * 前提としており、「その日に何を食べたか」は利用者のローカル日付で
     * 数えるのが自然なため。
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
