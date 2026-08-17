# calorie-tracker

カロリー・栄養を記録して可視化する Web アプリとモバイルアプリ。

このリポジトリには目的が 2 つあり、どちらも同格に扱っている。

1. 実際に使えるカロリー管理アプリを完成させる
2. **同一の API 仕様を Java と Kotlin の 2 実装で作り、言語差を比較する**

2 つ目のため、フレームワーク・DB・ORM・テスト基盤は意図的に両実装で揃えてある。片方だけ別のライブラリを入れると、コードの差が「言語の差」なのか「ライブラリの差」なのか判別できなくなるため。

## 構成

```
Next.js (Web)          Expo (iOS)          contract-tests
      │                    │                     │
      └────────────────────┴─────────┬───────────┘
                     │               │ 同じ検証を両方へ
      ┌──────────────┴──────────────┐
 Java + Spring Boot          Kotlin + Spring Boot
      :8080                        :8081
      └──────────────┬──────────────┘
                     │
              PostgreSQL 16
```

「2 実装が同じものである」ことを保証しているのは [`api/openapi.yaml`](api/openapi.yaml) の 1 ファイル。API を変更するときは、実装より先にこのファイルを直す。

その一致は [`contract-tests`](contract-tests) が実行して確かめる。同じ検証を両実装に流すだけでなく、**片方が発行したトークンを他方が検証できること**、**同じ入力に対する目標算出の応答が 1 バイトも違わないこと**まで確認している。

| 領域 | 採用 | 状態 |
|---|---|---|
| Web フロント | Next.js 16 (React / TypeScript) | 稼働 |
| モバイル | Expo 57 (React Native) | 稼働 |
| バックエンド A | Java 21 + Spring Boot 3 — `:8080` | 稼働 |
| バックエンド B | Kotlin + Spring Boot 3 — `:8081` | 稼働 |
| 契約テスト | 両実装へ同一検証（51 件） | 稼働 |
| DB | PostgreSQL 16（両実装で 1 インスタンス共有） | 稼働 |
| データアクセス | Spring Data JPA / Hibernate（両実装とも同じ） | 稼働 |
| マイグレーション | Flyway（Java 側に一元化） | 稼働 |
| 認証 | Spring Security + JWT | 稼働 |

## 起動方法

Docker Desktop を起動した状態で、ターミナルを複数使う。

```bash
# ターミナル 1 — DB と Java 実装（ホスト側ポートは 55432。Flyway もここで走る）
docker compose up -d db
cd backend-java && ./gradlew bootRun
```

**Java 側を必ず先に起動する。** Flyway を持つのは Java 側だけなので、Kotlin 側を先に立てるとスキーマが無く `ddl-auto=validate` で落ちる。

```bash
# ターミナル 2 — Kotlin 実装
cd backend-kotlin && ./gradlew bootRun
```

```bash
# ターミナル 3 — Web フロントエンド
cd frontend-web
cp .env.local.example .env.local
npm install && npm run dev
```

ブラウザで `http://localhost:3000` を開く。`.env.local` のポートを 8081 に変えると、同じ画面が Kotlin 実装に繋がる。

```bash
# ターミナル 4 — モバイル（.env に開発 PC の LAN IP を設定しておく）
cd mobile
cp .env.example .env
npm install && npx expo start
```

iPhone の Expo Go で QR を読むと実機で起動する。Apple Developer Program への加入は不要。

テストは DB を起動した状態で実行する。開発用の `calorie` ではなくテスト専用の `calorie_test` に接続するため、手元の開発データは壊れない。

```bash
cd backend-java && ./gradlew test
```

```bash
cd backend-kotlin && ./gradlew test
```

**契約テスト**は両バックエンドを起動した状態で実行する。同じ検証を `:8080` と `:8081` の両方に流し、外から見て同一に振る舞うことを確認する。

```bash
cd contract-tests && ./gradlew test
```

DB 不要のユニットテストだけを走らせる場合。

```bash
cd backend-java && ./gradlew test --tests "*GoalCalculatorTest" --tests "*NutritionTest"
```

詳しい手順・接続情報・設計判断の記録は、フェーズごとの HTML ドキュメントにまとめてある。**これらは開発環境のローカルにのみ置いており、このリポジトリには含めていない。**

## 設計上の要点

- **スキーマの所有者は Java 側だけ。** Flyway のマイグレーションは `backend-java` にのみ置き、Kotlin 側は `ddl-auto=validate` でスキーマとエンティティのずれを起動時に検知する。DB ユーザーも `app_java` / `app_kotlin` に分け、Kotlin 側には DDL 権限を与えていない。
- **食事記録は栄養値をスナップショットで保持する。** 食品マスタは成分表の改訂で変わるが、「その日に何 kcal 食べたか」は後から変わってはならない。意図的な非正規化。
- **Testcontainers は使わない。** Docker Desktop 29 系で Docker を検出できなかったため、Compose 上の `calorie_test` に接続して `@Transactional` でロールバックする方式にしている。両実装で同じ方式にすること。
- **栄養値はクライアントに計算させない。** 記録の API は食品 ID と分量だけを受け取り、換算はサーバーで行う。計算式が両側に存在すると Web とモバイルで結果がずれるため。
- **他人のリソースは 403 ではなく 404。** 403 だと「その ID は存在する」ことを教えてしまう。ID 指定のクエリには必ず `user_id` を条件に含める。
- **フロントの接続先は環境変数 1 つ。** `NEXT_PUBLIC_API_BASE_URL`（Web）／`EXPO_PUBLIC_API_BASE_URL`（モバイル）を 8081 に向ければ、同じ画面が Kotlin 実装に繋がる。2 実装が同じ契約を満たしているかの確認手段として使う。
- **契約テストは両実装のコードに依存しない。** HTTP で外から叩くだけの独立プロジェクトにしている。片方のクラスを import すると、その実装に引きずられた検証になるため。
- **Kotlin 側で設計を変えない。** 差分が言語の差か設計の差か判別できなくなるため、Exposed や coroutines の採用など「Kotlin ならこう書ける」は比較レポートの考察として分けて記録する。
- **null は省略せず明示的に返す。** Jackson の `non_null` は使わない。キーを消すと「値が null」と「キーが無い」の区別がクライアントに持ち込まれ、2 実装のあいだで暗黙の差異になりやすいため。

## 開発フェーズ

| Phase | 内容 | 状態 |
|---|---|---|
| 0 | 基盤（Docker Compose、Flyway 初期スキーマ、認証、OpenAPI 骨格） | 完了 |
| 1 | MVP 貫通（Java + Next.js：記録 → 日別集計 → グラフ） | 完了 |
| 2 | Kotlin 実装 + Expo アプリ + 契約テスト | 完了 |
| 3 | バーコード、詳細栄養素、週別・月別集計 | 未着手 |
| 4 | 運動記録、写真 AI 推定（モック） | 未着手 |
| 5 | 無料クラウドへデプロイ、言語比較レポート | 未着手 |

## 外部データ

| データ | ライセンス |
|---|---|
| 日本食品標準成分表（文部科学省） | 出典明記のうえ利用。取り込みは Phase 3。 |
| Open Food Facts | ODbL。出典表示が必要。Phase 3 で利用。 |

学習・ポートフォリオ用途。商用リリースは対象外。
