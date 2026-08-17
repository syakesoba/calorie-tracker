-- ============================================================================
-- V3: Kotlin 実装のロールに DML 権限を付与する
--
-- 【なぜマイグレーションで行うか】
-- db/init/01-roles.sql の ALTER DEFAULT PRIVILEGES は、初期化スクリプトが接続した
-- データベース（calorie）に対してのみ効く。デフォルト権限はデータベース単位のため、
-- テスト用の calorie_test には引き継がれない。
--
-- スキーマの所有者は app_java であり、他のロールへの権限付与は所有者の責務である。
-- Flyway は Java 側が接続しているデータベースで走るため、calorie と calorie_test の
-- どちらでも同じ権限状態が作られる。
--
-- 【付与するのは DML だけ】
-- CREATE 権限は与えない。Kotlin 側が誤ってスキーマを変更する事故を、
-- 権限レベルで防ぐという設計を維持する。
-- ============================================================================

DO $$
BEGIN
    -- クラウドへ展開した環境など、app_kotlin が存在しない場合もある。
    -- ロールが無いだけでマイグレーション全体が失敗しないようにする。
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_kotlin') THEN
        RAISE NOTICE 'ロール app_kotlin が存在しないため、権限付与をスキップします';
        RETURN;
    END IF;

    EXECUTE 'GRANT USAGE ON SCHEMA public TO app_kotlin';

    -- 既存のテーブル・シーケンスへの権限
    EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_kotlin';
    EXECUTE 'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_kotlin';

    -- 今後 app_java が作るテーブル・シーケンスへ自動で権限が付くようにする。
    -- テーブルを追加するたびに GRANT を書かずに済む。
    EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE app_java IN SCHEMA public '
         || 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_kotlin';
    EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE app_java IN SCHEMA public '
         || 'GRANT USAGE, SELECT ON SEQUENCES TO app_kotlin';
END
$$;
