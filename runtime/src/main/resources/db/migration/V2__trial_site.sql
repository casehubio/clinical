CREATE TABLE trial_site (
    id               UUID         NOT NULL,
    trial_id         UUID         NOT NULL,
    investigator_id  VARCHAR(255) NOT NULL,
    status           VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    CONSTRAINT pk_trial_site PRIMARY KEY (id),
    CONSTRAINT fk_trial_site_trial FOREIGN KEY (trial_id) REFERENCES clinical_trial(id)
);
