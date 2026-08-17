package com.example.calorie;

import com.example.calorie.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * カロリー管理アプリ Java バックエンドの起動クラス。
 *
 * <p>このアプリケーションは PostgreSQL のスキーマ所有者であり、
 * Flyway によるマイグレーションを実行する唯一のアプリケーションである。
 * Kotlin 側（ポート 8081）は同じ DB を参照するが、DDL は一切行わない。
 */
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class CalorieApplication {

    public static void main(String[] args) {
        SpringApplication.run(CalorieApplication.class, args);
    }
}
