-- ローカル開発用のロール作成。初回コンテナ起動時に一度だけ実行される。
--
-- 【設計意図】
-- Java 側と Kotlin 側で DB ユーザーを分けることで、pg_stat_activity や
-- スロークエリログを見たときに「どちらのアプリが発行した SQL か」を
-- 追跡できるようにする。
--
-- スキーマの所有者は app_java のみ。Flyway によるマイグレーション（DDL）は
-- Java 側だけが実行する。app_kotlin には DML 権限しか与えない。
-- これにより「Kotlin 側が誤ってスキーマを変更する」事故を権限レベルで防ぐ。
--
-- 注意: ここのパスワードはローカル開発専用。クラウドへ展開する際は必ず変更する。

CREATE ROLE app_java   WITH LOGIN PASSWORD 'app_java_pw';
CREATE ROLE app_kotlin WITH LOGIN PASSWORD 'app_kotlin_pw';

-- public スキーマの所有権を app_java へ移す（PostgreSQL 15 以降、public への
-- CREATE 権限はデフォルトで付与されないため、明示的に所有者を変える）
ALTER SCHEMA public OWNER TO app_java;

GRANT CONNECT ON DATABASE calorie TO app_java, app_kotlin;
GRANT USAGE  ON SCHEMA public     TO app_kotlin;

-- app_java が今後作るテーブル／シーケンスに対して、app_kotlin へ自動で
-- DML 権限が付くようにする。テーブルを追加するたびに GRANT を書かずに済む。
ALTER DEFAULT PRIVILEGES FOR ROLE app_java IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_kotlin;

ALTER DEFAULT PRIVILEGES FOR ROLE app_java IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO app_kotlin;
