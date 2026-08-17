package com.example.calorie.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * メールアドレスでユーザーを引く。
     * 引数は必ず {@link User#normalizeEmail} を通した値を渡すこと。
     */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
