package com.example.calorie.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    fun findByTokenHash(tokenHash: String): RefreshToken?

    /**
     * 指定ユーザーの未失効トークンをすべて失効させる。
     * 「他の端末からログアウトする」ための操作。
     */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    fun revokeAllByUserId(@Param("userId") userId: Long, @Param("now") now: OffsetDateTime): Int
}
