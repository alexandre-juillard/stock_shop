-- =========================================================
-- V1 : schéma initial de l'application Stock & Shop
-- Conventions : id UUID (gen_random_uuid()), timestamps TIMESTAMPTZ,
-- quantités DECIMAL(10,3), tokens sensibles toujours hachés.
-- =========================================================

-- ---------------------------------------------------------
-- Types énumérés
-- ---------------------------------------------------------
CREATE TYPE push_platform AS ENUM ('android', 'ios');

-- ---------------------------------------------------------
-- users
-- NB : la colonne "role" (ADMIN/USER) est ajoutée en plus du
-- schéma de base.
-- ---------------------------------------------------------
CREATE TABLE users (
    id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                          VARCHAR(255) NOT NULL,
    first_name                     VARCHAR(100) NOT NULL,
    last_name                      VARCHAR(100) NOT NULL,
    password_hash                  VARCHAR(255),
    avatar_url                     VARCHAR(500),
    role                           VARCHAR(20) NOT NULL DEFAULT 'USER',
    is_active                      BOOLEAN NOT NULL DEFAULT FALSE,
    email_confirmed_at             TIMESTAMPTZ,
    confirmation_token_hash        VARCHAR(255),
    confirmation_token_expires_at  TIMESTAMPTZ,
    reset_token_hash               VARCHAR(255),
    reset_token_expires_at         TIMESTAMPTZ,
    expiration_alert_days          INT NOT NULL DEFAULT 3,
    theme                          VARCHAR(10) NOT NULL DEFAULT 'light',
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'USER')),
    CONSTRAINT chk_users_expiration_alert_days CHECK (expiration_alert_days > 0),
    CONSTRAINT chk_users_theme CHECK (theme IN ('light', 'dark'))
);
CREATE INDEX idx_users_email ON users (email);

-- ---------------------------------------------------------
-- oauth_accounts
-- ---------------------------------------------------------
CREATE TABLE oauth_accounts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider          VARCHAR(50) NOT NULL,
    provider_user_id  VARCHAR(255) NOT NULL,
    provider_email    VARCHAR(255) NOT NULL,
    linked_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_oauth_accounts_provider UNIQUE (provider, provider_user_id)
);
CREATE INDEX idx_oauth_accounts_user_id ON oauth_accounts (user_id);

-- ---------------------------------------------------------
-- oauth_link_decisions
-- ---------------------------------------------------------
CREATE TABLE oauth_link_decisions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider          VARCHAR(50) NOT NULL,
    provider_user_id  VARCHAR(255) NOT NULL,
    linked            BOOLEAN NOT NULL,
    decided_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_oauth_link_decisions UNIQUE (user_id, provider, provider_user_id)
);

-- ---------------------------------------------------------
-- user_sessions ("Se souvenir de moi", 7 jours)
-- ---------------------------------------------------------
CREATE TABLE user_sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    user_agent  VARCHAR(500),
    CONSTRAINT uq_user_sessions_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_user_sessions_token_hash ON user_sessions (token_hash);
CREATE INDEX idx_user_sessions_user_id ON user_sessions (user_id);

-- ---------------------------------------------------------
-- push_tokens
-- ---------------------------------------------------------
CREATE TABLE push_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token       VARCHAR(500) NOT NULL,
    platform    push_platform NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_push_tokens_token UNIQUE (token)
);
CREATE INDEX idx_push_tokens_user_id ON push_tokens (user_id);

-- ---------------------------------------------------------
-- quantity_types (référentiel, seed en V2)
-- ---------------------------------------------------------
CREATE TABLE quantity_types (
    id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code   VARCHAR(20) NOT NULL,
    label  VARCHAR(50) NOT NULL,
    CONSTRAINT uq_quantity_types_code UNIQUE (code)
);

-- ---------------------------------------------------------
-- quantity_units (référentiel, seed en V2)
-- ---------------------------------------------------------
CREATE TABLE quantity_units (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quantity_type_id   UUID NOT NULL REFERENCES quantity_types (id) ON DELETE RESTRICT,
    code               VARCHAR(10) NOT NULL,
    label              VARCHAR(50) NOT NULL,
    conversion_factor  DECIMAL(20, 10) NOT NULL,
    is_base_unit       BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order         INT NOT NULL,
    CONSTRAINT uq_quantity_units_type_code UNIQUE (quantity_type_id, code)
);
CREATE INDEX idx_quantity_units_type_id ON quantity_units (quantity_type_id);

-- ---------------------------------------------------------
-- categories
-- ---------------------------------------------------------
CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    color       CHAR(7) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_categories_user_name UNIQUE (user_id, name),
    CONSTRAINT chk_categories_color CHECK (color ~ '^#[0-9A-Fa-f]{6}$')
);
CREATE INDEX idx_categories_user_id ON categories (user_id);

-- ---------------------------------------------------------
-- products
-- ---------------------------------------------------------
CREATE TABLE products (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id        UUID NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    name               VARCHAR(200) NOT NULL,
    quantity_type_id   UUID NOT NULL REFERENCES quantity_types (id) ON DELETE RESTRICT,
    base_unit_id       UUID NOT NULL REFERENCES quantity_units (id) ON DELETE RESTRICT,
    photo_url          VARCHAR(500),
    is_visible         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_products_user_name UNIQUE (user_id, name)
);
CREATE INDEX idx_products_user_id ON products (user_id);
CREATE INDEX idx_products_category_id ON products (category_id);

-- ---------------------------------------------------------
-- stock_items
-- ---------------------------------------------------------
CREATE TABLE stock_items (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id                UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    quantity                  DECIMAL(10, 3) NOT NULL DEFAULT 0,
    low_threshold             DECIMAL(10, 3),
    expiration_date           DATE,
    last_expiry_notified_at   DATE,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_stock_items_user_product UNIQUE (user_id, product_id),
    CONSTRAINT chk_stock_items_quantity CHECK (quantity >= 0),
    CONSTRAINT chk_stock_items_low_threshold CHECK (low_threshold IS NULL OR low_threshold >= 0)
);
CREATE INDEX idx_stock_items_user_id ON stock_items (user_id);
CREATE INDEX idx_stock_items_product_id ON stock_items (product_id);
CREATE INDEX idx_stock_items_expiration_date ON stock_items (expiration_date)
    WHERE expiration_date IS NOT NULL;

-- ---------------------------------------------------------
-- shopping_list_items
-- ---------------------------------------------------------
CREATE TABLE shopping_list_items (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id            UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    is_checked            BOOLEAN NOT NULL DEFAULT FALSE,
    checked_quantity      DECIMAL(10, 3),
    checked_unit_id       UUID REFERENCES quantity_units (id) ON DELETE RESTRICT,
    added_automatically   BOOLEAN NOT NULL DEFAULT FALSE,
    added_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    checked_at            TIMESTAMPTZ,
    CONSTRAINT uq_shopping_list_items_user_product UNIQUE (user_id, product_id),
    CONSTRAINT chk_shopping_list_items_checked_quantity
        CHECK (checked_quantity IS NULL OR checked_quantity > 0)
);
CREATE INDEX idx_shopping_list_user_id ON shopping_list_items (user_id);

-- ---------------------------------------------------------
-- recipes
-- ---------------------------------------------------------
CREATE TABLE recipes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_recipes_user_id ON recipes (user_id);

-- ---------------------------------------------------------
-- recipe_ingredients
-- ---------------------------------------------------------
CREATE TABLE recipe_ingredients (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id   UUID NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    product_id  UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    quantity    DECIMAL(10, 3) NOT NULL,
    unit_id     UUID NOT NULL REFERENCES quantity_units (id) ON DELETE RESTRICT,
    CONSTRAINT uq_recipe_ingredients_recipe_product UNIQUE (recipe_id, product_id),
    CONSTRAINT chk_recipe_ingredients_quantity CHECK (quantity > 0)
);
CREATE INDEX idx_recipe_ingredients_recipe_id ON recipe_ingredients (recipe_id);
CREATE INDEX idx_recipe_ingredients_product_id ON recipe_ingredients (product_id);

