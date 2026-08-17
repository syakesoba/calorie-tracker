# calorie-tracker

カロリー・栄養を記録して可視化する Web アプリとモバイルアプリ。

このリポジトリには目的が 2 つあり、どちらも同格に扱っている。

1. 実際に使えるカロリー管理アプリを完成させる
2. **同一の API 仕様を Java と Kotlin の 2 実装で作り、言語差を比較する**

2 つ目のため、フレームワーク・DB・ORM・テスト基盤は意図的に両実装で揃えてある。片方だけ別のライブラリを入れると、コードの差が「言語の差」なのか「ライブラリの差」なのか判別できなくなるため。

## 構成

```
Next.js (Web)          Expo (iOS/Android)
      │                        │
      └──── 生成された TypeScript クライアント ────┘
                     │
      ┌──────────────┴──────────────┐
 Java + Spring Boot          Kotlin + Spring Boot
      :8080                        :8081
      └──────────────┬──────────────┘
                     │
              PostgreSQL 16
```

「2 実装が同じものである」ことを保証しているのは [`api/openapi.yaml`](api/openapi.yaml) の 1 ファイル。API を変更するときは、実装より先にこのファイルを直す。

| 領域 | 採用 | 状態 |
|---|---|---|
| Web フロント | Next.js 16 (React / TypeScript) | 稼働 |
| モバイル | Expo (React Native) | Phase 2 |
| バックエンド A | Java 21 + Spring Boot 3 — `:8080` | 稼働 |
| バックエンド B | Kotlin + Spring Boot 3 — `:8081` | Phase 2 |
| DB | PostgreSQL 16（両実装で 1 インスタンス共有） | 稼働 |
| データアクセス | Spring Data JPA / Hibernate（両実装とも同じ） | 稼働 |
| マイグレーション | Flyway（Java 側に一元化） | 稼働 |
| 認証 | Spring Security + JWT | 稼働 |

## 起動方法

Docker Desktop を起動した状態で、ターミナルを 2 つ使う。

```bash
# ターミナル 1 — DB とバックエンド（ホスト側ポートは 55432）
docker compose up -d db
cd backend-java && ./gradlew bootRun
```

```bash
# ターミナル 2 — Web フロントエンド
cd frontend-web
cp .env.local.example .env.local
npm install && npm run dev
```

ブラウザで `http://localhost:3000` を開く。

テストは DB を起動した状態で実行する。開発用の `calorie` ではなくテスト専用の `calorie_test` に接続するため、手元の開発データは壊れない。

```bash
cd backend-java && ./gradlew test
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
- **フロントの接続先は環境変数 1 つ。** `NEXT_PUBLIC_API_BASE_URL` を 8081 に向ければ同じ画面が Kotlin 実装に繋がる。2 実装が同じ契約を満たしているかの確認手段として使う。
- **null は省略せず明示的に返す。** Jackson の `non_null` は使わない。キーを消すと「値が null」と「キーが無い」の区別がクライアントに持ち込まれ、2 実装のあいだで暗黙の差異になりやすいため。

## 開発フェーズ

| Phase | 内容 | 状態 |
|---|---|---|
| 0 | 基盤（Docker Compose、Flyway 初期スキーマ、認証、OpenAPI 骨格） | 完了 |
| 1 | MVP 貫通（Java + Next.js：記録 → 日別集計 → グラフ） | 完了 |
| 2 | Kotlin 実装 + Expo アプリ + 契約テスト | 未着手 |
| 3 | バーコード、詳細栄養素、週別・月別集計 | 未着手 |
| 4 | 運動記録、写真 AI 推定（モック） | 未着手 |
| 5 | 無料クラウドへデプロイ、言語比較レポート | 未着手 |

## 外部データ

| データ | ライセンス |
|---|---|
| 日本食品標準成分表（文部科学省） | 出典明記のうえ利用。取り込みは Phase 3。 |
| Open Food Facts | ODbL。出典表示が必要。Phase 3 で利用。 |

学習・ポートフォリオ用途。商用リリースは対象外。
