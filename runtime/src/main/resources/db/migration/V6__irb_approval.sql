CREATE TABLE irb_approval (
    id               UUID         NOT NULL,
    site_id          UUID         NOT NULL,
    review_type      VARCHAR(255) NOT NULL,
    committee_id     VARCHAR(255) NOT NULL,
    decision_deadline TIMESTAMP   NOT NULL,
    decision         VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    CONSTRAINT pk_irb_approval PRIMARY KEY (id),
    CONSTRAINT fk_irb_site FOREIGN KEY (site_id) REFERENCES trial_site(id)
);
