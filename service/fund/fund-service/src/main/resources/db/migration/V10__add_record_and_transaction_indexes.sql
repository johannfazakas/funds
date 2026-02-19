CREATE INDEX record_user_id_idx ON record (user_id);
CREATE INDEX record_transaction_id_idx ON record (transaction_id);
CREATE INDEX record_account_id_idx ON record (account_id);
CREATE INDEX record_fund_id_idx ON record (fund_id);
CREATE INDEX record_unit_idx ON record (unit);

CREATE INDEX transaction_user_id_idx ON transaction (user_id);
CREATE INDEX transaction_date_time_idx ON transaction (date_time);

CREATE INDEX record_labels_gin ON record USING GIN (labels);
