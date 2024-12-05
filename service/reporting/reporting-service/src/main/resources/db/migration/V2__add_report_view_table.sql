CREATE TABLE report_view
(
    id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID        NOT NULL,
    name    VARCHAR(50) NOT NULL,
    fund_id UUID        NOT NULL,
    type    VARCHAR(50) NOT NULL
);
