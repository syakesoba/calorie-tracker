package com.example.calorie.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {

    /**
     * メールアドレスでユーザーを引く。
     * 引数は必ず [User.normalizeEmail] を通した値を渡すこと。
     *
     * Java 側は `Optional<User>` を返しているが、Kotlin では null 許容型で表す。
     * Spring Data は戻り値の null 許容性を解釈できるため、宣言はこれで足りる。
     */
    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean
}
