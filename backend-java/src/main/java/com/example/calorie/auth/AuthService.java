package com.example.calorie.auth;

import com.example.calorie.auth.dto.AuthDtos.LoginRequest;
import com.example.calorie.auth.dto.AuthDtos.SignUpRequest;
import com.example.calorie.auth.dto.AuthDtos.TokenResponse;
import com.example.calorie.auth.dto.AuthDtos.UserResponse;
import com.example.calorie.common.ApiException;
import com.example.calorie.user.User;
import com.example.calorie.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

/**
 * 登録・ログイン・トークン更新・ログアウト。
 */
@Service
public class AuthService {

    /** BCrypt はパスワードを 72 バイトまでしか扱えない。日本語は 1 文字 3 バイトになりうる。 */
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    /**
     * ユーザーが存在しない場合でもパスワード照合と同等の時間をかけるためのダミーハッシュ。
     * 応答時間の差からメールアドレスの存在有無を推測されるのを防ぐ。
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public TokenResponse signUp(SignUpRequest request) {
        validatePasswordLength(request.password());

        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_USED", "このメールアドレスは既に登録されています。");
        }

        User user = userRepository.save(
                User.create(email, passwordEncoder.encode(request.password()), request.displayName())
        );
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = User.normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).orElse(null);

        // ユーザーが見つからない場合もダミーハッシュと照合し、処理時間を揃える
        String storedHash = (user == null) ? DUMMY_HASH : user.getPasswordHash();
        boolean matches = passwordEncoder.matches(request.password(), storedHash);

        if (user == null || !matches) {
            // どちらが原因かをクライアントへ伝えない（メールアドレスの存在推測を防ぐ）
            throw ApiException.unauthorized(
                    "INVALID_CREDENTIALS", "メールアドレスまたはパスワードが正しくありません。");
        }

        return issueTokens(user);
    }

    /**
     * リフレッシュトークンを使って新しいトークン一式を発行する。
     *
     * <p>使用済みのトークンはその場で失効させ、新しいものを発行する（ローテーション）。
     * 盗まれたトークンが再利用されたときに、正規の利用者が先に使っていれば弾ける。
     */
    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        String hash = jwtService.hashRefreshToken(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.unauthorized(
                        "INVALID_REFRESH_TOKEN", "リフレッシュトークンが無効です。再度ログインしてください。"));

        if (!token.isUsable(OffsetDateTime.now())) {
            throw ApiException.unauthorized(
                    "INVALID_REFRESH_TOKEN", "リフレッシュトークンの有効期限が切れています。再度ログインしてください。");
        }

        token.revoke();

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> ApiException.unauthorized(
                        "INVALID_REFRESH_TOKEN", "リフレッシュトークンが無効です。再度ログインしてください。"));

        return issueTokens(user);
    }

    /**
     * ログアウト。渡されたリフレッシュトークンを失効させる。
     *
     * <p>存在しないトークンを渡されてもエラーにしない。ログアウトは冪等であるべきで、
     * かつ「そのトークンが存在するか」を攻撃者に教えないため。
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = jwtService.hashRefreshToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(RefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "ユーザーが見つかりません。"));
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail());
        String rawRefreshToken = jwtService.generateRefreshTokenValue();

        refreshTokenRepository.save(RefreshToken.issue(
                user.getId(),
                jwtService.hashRefreshToken(rawRefreshToken),
                jwtService.refreshTokenExpiresAt()
        ));

        return TokenResponse.of(accessToken, rawRefreshToken, jwtService.accessTokenExpiresInSeconds());
    }

    private void validatePasswordLength(String password) {
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > BCRYPT_MAX_PASSWORD_BYTES) {
            throw ApiException.badRequest(
                    "PASSWORD_TOO_LONG",
                    "パスワードが長すぎます。UTF-8 で " + BCRYPT_MAX_PASSWORD_BYTES + " バイト以内にしてください。");
        }
    }
}
