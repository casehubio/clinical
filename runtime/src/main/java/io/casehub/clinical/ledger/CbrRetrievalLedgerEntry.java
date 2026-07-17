package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;

@Entity
@Table(name = "cbr_retrieval_ledger_entry")
@DiscriminatorValue("CBR_RETRIEVAL")
public class CbrRetrievalLedgerEntry extends JpaLedgerEntry {

    @Column(name = "retrieval_trace_id", nullable = false, length = 36)
    public String retrievalTraceId;

    @Column(name = "query_domain", nullable = false, length = 50)
    public String queryDomain;

    @Column(name = "query_features_summary", nullable = false, length = 2000)
    public String queryFeaturesSummary;

    @Column(name = "retrieved_case_count", nullable = false)
    public int retrievedCaseCount;

    @Column(name = "top_score", nullable = false)
    public double topScore;

    @Column(name = "explanation_text", length = 10000)
    public String explanationText;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                retrievalTraceId     != null ? retrievalTraceId     : "",
                queryDomain          != null ? queryDomain          : "",
                queryFeaturesSummary != null ? queryFeaturesSummary : "",
                String.valueOf(retrievedCaseCount),
                String.valueOf(topScore),
                explanationText      != null ? explanationText      : "")
                .getBytes(StandardCharsets.UTF_8);
    }
}
