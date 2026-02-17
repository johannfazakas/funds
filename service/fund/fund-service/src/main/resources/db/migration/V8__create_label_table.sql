CREATE TABLE label (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    name VARCHAR(50) NOT NULL,
    UNIQUE(user_id, name)
);
