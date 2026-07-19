-- Phase 17A: extend evaluation_case.case_type to support real-data
-- evaluation cases for clustering pairs and ranking relevance.
--
-- PostgreSQL CHECK constraints cannot be extended in place, so the existing
-- Phase 8 constraint is dropped and recreated with the two new case types
-- appended. All five Phase 8 case types are preserved so historical
-- evaluation_case rows keep validating.

ALTER TABLE evaluation_case
    DROP CONSTRAINT IF EXISTS ck_evaluation_case_type;

ALTER TABLE evaluation_case
    ADD CONSTRAINT ck_evaluation_case_type
        CHECK (case_type IN (
            'CRAWL_ITEM_PRESENT',
            'CLUSTER_MEMBERSHIP',
            'SCORE_THRESHOLD',
            'ANALYSIS_REQUIRED_FIELDS',
            'ALERT_EXPECTED_RECORD',
            'CLUSTER_PAIR_EXPECTATION',
            'RANKING_RELEVANCE_EXPECTATION'
        ));
