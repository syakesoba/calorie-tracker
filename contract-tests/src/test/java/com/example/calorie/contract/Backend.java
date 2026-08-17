package com.example.calorie.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 検証対象のバックエンド 1 つ分。
 *
 * <p>実装のコードには一切依存せず、HTTP で外から叩くだけ。
 * どちらの実装かは {@link #name()} でしか区別しない。
 */
public final class Backend {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final String baseUrl;
    private final HttpClient client;

    private Backend(String name, String baseUrl) {
        this.name = name;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * システムプロパティ {@code contract.backends} から検証対象を読む。
     * 形式は {@code name=url} のカンマ区切り。
     */
    public static List<Backend> fromSystemProperty() {
        String raw = System.getProperty("contract.backends");
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                    "システムプロパティ contract.backends が指定されていません。"
                            + " 例: -Pbackends=\"java=http://localhost:8080/api,kotlin=http://localhost:8081/api\"");
        }
        List<Backend> backends = new ArrayList<>();
        for (String entry : raw.split(",")) {
            String[] parts = entry.trim().split("=", 2);
            if (parts.length != 2) {
                throw new IllegalStateException("contract.backends の形式が不正です: " + entry);
            }
            backends.add(new Backend(parts[0].trim(), parts[1].trim()));
        }
        return backends;
    }

    public String name() {
        return name;
    }

    /** JUnit の表示名に使う。どちらの実装で落ちたかが一目で分かるようにする。 */
    @Override
    public String toString() {
        return name + " (" + baseUrl + ")";
    }

    // ------------------------------------------------------------------ HTTP

    public Response get(String path, String accessToken) {
        return send(requestBuilder(path, accessToken).GET());
    }

    public Response post(String path, String body, String accessToken) {
        return send(requestBuilder(path, accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body)));
    }

    public Response put(String path, String body, String accessToken) {
        return send(requestBuilder(path, accessToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body)));
    }

    public Response delete(String path, String accessToken) {
        return send(requestBuilder(path, accessToken).DELETE());
    }

    private HttpRequest.Builder requestBuilder(String path, String accessToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15));
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        return builder;
    }

    private Response send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = client.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new IllegalStateException(
                    name + " に接続できません。起動しているか確認してください: " + baseUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("リクエストが中断されました", e);
        }
    }

    // --------------------------------------------------------------- 補助操作

    /** 新しいユーザーを作り、トークン一式を返す。 */
    public Tokens signUpNewUser() {
        String email = "contract-" + UUID.randomUUID() + "@example.com";
        return signUp(email, DEFAULT_PASSWORD);
    }

    public Tokens signUp(String email, String password) {
        Response response = post("/auth/signup", """
                {"email":"%s","password":"%s","displayName":"契約テスト"}
                """.formatted(email, password), null);
        if (response.status() != 201) {
            throw new IllegalStateException(
                    name + " での登録に失敗しました: " + response.status() + " " + response.body());
        }
        JsonNode json = response.json();
        return new Tokens(email, password,
                json.get("accessToken").asText(), json.get("refreshToken").asText());
    }

    public static final String DEFAULT_PASSWORD = "password1234";

    /** 検索で最初に見つかった食品の ID。 */
    public long findFoodId(String query, String accessToken) {
        Response response = get("/foods?query=" + encode(query), accessToken);
        if (response.status() != 200) {
            throw new IllegalStateException(
                    name + " の食品検索に失敗しました: " + response.status() + " " + response.body());
        }
        JsonNode foods = response.json();
        if (!foods.isArray() || foods.isEmpty()) {
            throw new IllegalStateException(
                    name + " で食品「" + query + "」が見つかりません。シードデータが未適用の可能性があります。");
        }
        return foods.get(0).get("id").asLong();
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 登録直後のトークンと、その資格情報。 */
    public record Tokens(String email, String password, String accessToken, String refreshToken) {
    }

    /** HTTP 応答。ステータスと本文だけを扱う。 */
    public record Response(int status, String body) {

        public JsonNode json() {
            try {
                return MAPPER.readTree(body);
            } catch (IOException e) {
                throw new IllegalStateException("JSON として解釈できません: " + body, e);
            }
        }

        /** エラー応答の {@code code}。 */
        public String errorCode() {
            return json().path("code").asText();
        }

        /** 指定パスのキーが存在するか。値が null でも true になる。 */
        public boolean hasKey(String field) {
            return json().has(field);
        }

        public boolean isNull(String field) {
            return json().get(field).isNull();
        }
    }

    /** JSON の値をたどるための補助。 */
    public static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /** 順序を無視してキー集合を比較したいとき用。 */
    public static List<String> sortedFieldNames(JsonNode node) {
        List<String> names = fieldNames(node);
        String[] array = names.toArray(new String[0]);
        Arrays.sort(array);
        return List.of(array);
    }
}
