CREATE TABLE dashboard
(
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id                 UUID         NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    position                INT          NOT NULL,
    default_granularity     VARCHAR(20)  NOT NULL,
    default_lookback_amount INT          NOT NULL,
    default_lookback_unit   VARCHAR(20)  NOT NULL,
    default_target_currency VARCHAR(20)  NOT NULL
);

CREATE INDEX dashboard_user_id_idx ON dashboard (user_id);

CREATE TABLE dashboard_chart
(
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    dashboard_id UUID         NOT NULL REFERENCES dashboard (id) ON DELETE CASCADE,
    name         VARCHAR(255) NOT NULL,
    position     INT          NOT NULL,
    queries      JSONB        NOT NULL
);

CREATE INDEX dashboard_chart_dashboard_id_idx ON dashboard_chart (dashboard_id);
