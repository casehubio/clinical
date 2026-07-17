-- Ledger entry for CBR precedent consultation audit trail
CREATE TABLE cbr_retrieval_ledger_entry (
    id                      UUID PRIMARY KEY REFERENCES ledger_entry(id),
    retrieval_trace_id      VARCHAR(36)    NOT NULL,
    query_domain            VARCHAR(50)    NOT NULL,
    query_features_summary  VARCHAR(2000)  NOT NULL,
    retrieved_case_count    INT            NOT NULL,
    top_score               DOUBLE PRECISION NOT NULL,
    explanation_text        VARCHAR(10000)
);
