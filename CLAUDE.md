# CLAUDE.md

カロリー管理アプリ開発プロジェクト。Claude Code はこのファイルの内容を常に守ること。

---

## 1. コミュニケーションのルール

- **返答・作業報告・説明はすべて日本語で行う。**
- コミットメッセージ、Issue、PR の説明も日本語で書く。
- コード内のコメント・Javadoc・KDoc も日本語で書く。
- 変数名・クラス名・メソッド名・テーブル名・カラム名は英語（日本語の識別子は使わない）。

## 2. ドキュメントのルール

**計画・詳細設計・起動方法は、その都度 `docs/` 配下に HTML ファイルとしてまとめること。**

- 保存先は `docs/`。ファイル名は `NN-<内容>.html`（例: `00-phase0-setup.html`）。
- 新しいドキュメントを追加したら、必ず `docs/index.html` の一覧に追記する。
- 更新タイミング:
  - 各フェーズに着手する前 → そのフェーズの**計画と詳細設計**
  - 実装が完了した時点 → **起動方法・動作確認手順**を追記または更新
  - 設計判断を変更した時点 → 該当ドキュメントを更新し、**変更理由を残す**
- **外部ホストを参照しない。** CDN のスクリプト・スタイル・Web フォントは使わない。オフラインでも開けること。
- 共通スタイルは `docs/assets/doc.css` に置き、各 HTML から相対パスで読み込む。**ドキュメントごとに見た目を作り直さない。**
- ライト／ダークの両テーマで読めるようにする（`prefers-color-scheme` に対応させ、色は必ず CSS 変数経由で指定する）。

## 3. プロジェクトの目的

1. カロリー・栄養を記録して可視化する Web アプリと モバイルアプリを完成させる。
2. **同一の API 仕様を Java と Kotlin の 2 実装で作り、言語差を比較する。**（1 と同格の目的）

学習・ポートフォリオ用途。商用リリースは対象外。

## 4. 技術構成

| 領域 | 採用 |
|---|---|
| Web フロント | Next.js (React / TypeScript) |
| モバイル | Expo (React Native) ※ macOS 非保有のため SwiftUI は不可 |
| バックエンド A | Java 21 + Spring Boot 3 — ポート `8080` |
| バックエンド B | Kotlin + Spring Boot 3 — ポート `8081` |
| DB | PostgreSQL 16（**両バックエンドで 1 インスタンスを共有**） |
| データアクセス | Spring Data JPA / Hibernate（**両実装とも同じ**） |
| マイグレーション | Flyway（**Java 側に一元化**） |
| 認証 | Spring Security + JWT（メール＋パスワード） |
| API 仕様 | OpenAPI 3.1（`api/openapi.yaml` が単一の正） |
| ビルド | Gradle (Kotlin DSL) |
| 実行環境 | Docker Compose（ローカル）→ 将来 Neon + Render |

## 5. 絶対に守る設計制約

- **比較の純度を守る。** Java 側と Kotlin 側で、フレームワーク・ライブラリ・DB・ORM・テスト基盤を意図的に揃える。片方だけ別のライブラリを入れると比較が成立しなくなるため、変更する場合は必ず両方に入れる。
- **スキーマの所有者は Java 側だけ。** Flyway のマイグレーションは `backend-java/src/main/resources/db/migration` にのみ置く。Kotlin 側は `spring.flyway.enabled=false` かつ `ddl-auto=validate` とし、スキーマとエンティティのずれを起動時に検知させる。
- **API を変更するときは、先に `api/openapi.yaml` を直す。** 実装を先に書かない。仕様を直したら両バックエンドを追従させる。
- **`meal_log_items` は栄養値をスナップショットで保持する。** 食品マスタを参照して都度計算しない。マスタ改訂で過去の記録が書き換わることを防ぐための意図的な非正規化であり、正規化のために削ってはならない。
- **DB ユーザーを分ける。** `app_java` / `app_kotlin`。どちらが発行した SQL か追えるようにする。

## 6. コストのルール

**このプロジェクトの予算は 0 円。**

- 課金が発生する選択肢（有料 API、有料ホスティング、Apple Developer Program 等）は、**採用する前に必ずユーザーに確認する。**
- 無断で有料サービスのアカウント作成・課金設定を行わない。

## 7. ディレクトリ構成

```
02_Java/
├── CLAUDE.md
├── docker-compose.yml
├── .env.example
├── api/
│   └── openapi.yaml          # API 仕様の単一の正
├── backend-java/             # Java 21 + Spring Boot（Flyway 所有）
├── backend-kotlin/           # Kotlin + Spring Boot（Phase 2）
├── frontend-web/             # Next.js（Phase 1）
├── mobile/                   # Expo（Phase 2）
├── db/
│   └── init/                 # DB ユーザー・DB 作成の初期化スクリプト
└── docs/                     # 計画・詳細設計・起動方法の HTML
    ├── index.html
    └── assets/doc.css
```

## 8. 開発フェーズ

| Phase | 内容 | 状態 |
|---|---|---|
| 0 | 基盤（Docker Compose、Flyway 初期スキーマ、認証、OpenAPI 骨格） | **完了** |
| 1 | MVP 貫通（Java + Next.js：記録 → 日別集計 → グラフ） | 実装完了（結合テスト未実行） |
| 2 | Kotlin 実装 + Expo アプリ + 契約テスト | 未着手 |
| 3 | バーコード、詳細栄養素、週別・月別集計 | 未着手 |
| 4 | 運動記録、写真 AI 推定（モック） | 未着手 |
| 5 | 無料クラウドへデプロイ、言語比較レポート | 未着手 |

フェーズを進めたら、この表の状態を更新すること。

## 9. よく使うコマンド

```bash
# DB 起動（ホスト側ポートは 55432。ローカル常駐の PostgreSQL 16/18 と衝突を避けるため）
docker compose up -d db

# Java バックエンド起動（Flyway のマイグレーションもここで走る）
cd backend-java && ./gradlew bootRun

# テスト（事前に DB を起動しておくこと。calorie_test に接続する）
cd backend-java && ./gradlew test

# DB 不要のユニットテストだけ
cd backend-java && ./gradlew test --tests "*GoalCalculatorTest" --tests "*NutritionTest"

# Web フロントエンド起動（別ターミナル）
cd frontend-web && npm run dev
```

## 10. 環境固有の注意

- **DB のホスト側ポートは 55432。** この PC には PostgreSQL 16 / 18 が Windows サービスとして常駐し 5432 を占有しているため。既存のローカル PostgreSQL には手を加えない。
- **Testcontainers は使わない。** Docker Desktop 29 系で docker-java が Docker を検出できないため。テストは Compose 上の `calorie_test` に接続し、`@Transactional` でロールバックする。**Kotlin 側も同じ方式にすること**（テスト基盤を揃えないと言語比較が成立しない）。
- **`db/init/*.sql` はボリュームが空のときしか実行されない。** 変更を反映するには `docker compose down -v` が必要。

詳細な手順は `docs/` 配下の各 HTML を参照。
