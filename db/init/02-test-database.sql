-- テスト専用データベース。
--
-- 開発用の calorie とテスト用の calorie_test を分けることで、
-- テスト実行が手元の開発データを壊さないようにする。
--
-- OWNER を app_java にすると、PostgreSQL 15 以降の pg_database_owner の仕組みにより
-- app_java が public スキーマに対して CREATE 権限を持つ。Flyway はこの権限で動く。

CREATE DATABASE calorie_test OWNER app_java;

GRANT CONNECT ON DATABASE calorie_test TO app_java;
