CREATE TABLE report_record
(
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id        UUID NOT NULL,
    report_view_id UUID NOT NULL REFERENCES report_view (id),
    date           DATE NOT NULL,
    amount         NUMERIC(20, 8) NOT NULL
);

CREATE INDEX idx_report_record_date ON report_record (date);
