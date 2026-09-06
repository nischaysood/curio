-- Curio schema, v1.
--
-- Neon lives behind the Worker and is never reachable from the app. The app
-- holds a session token; the Worker holds the connection string. Nothing that
-- ships in an APK can address this database, which is the whole point.
--
-- Apply with:  psql "$DATABASE_URL" -f migrations/001_init.sql

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
-- Email + password hash. No third-party identity yet: Google Sign-In means a
-- Play Console OAuth client and an Apple equivalent, and neither is worth a day
-- before the deadline. The column is nullable so social login can be added
-- later without a migration that breaks existing rows.

CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    email         TEXT        NOT NULL,
    password_hash TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Case-insensitive uniqueness. Storing the raw email but comparing folded means
-- "Nischay@x.com" and "nischay@x.com" can't become two accounts — a class of
-- support ticket that is miserable to untangle after the fact.
CREATE UNIQUE INDEX IF NOT EXISTS users_email_lower_idx ON users (lower(email));

-- ---------------------------------------------------------------------------
-- sessions
-- ---------------------------------------------------------------------------
-- Opaque random tokens, stored hashed. If this table leaks, the tokens in it
-- are useless — same reasoning as password_hash. A JWT would avoid the lookup
-- but can't be revoked, and "log out everywhere" is a feature people expect.

CREATE TABLE IF NOT EXISTS sessions (
    token_hash TEXT        PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS sessions_user_idx ON sessions (user_id);

-- ---------------------------------------------------------------------------
-- courses
-- ---------------------------------------------------------------------------
-- Deduplicated by topic_hash, exactly like the KV cache — a course on Big-O is
-- byte-identical for every learner, so it is stored once and referenced by
-- everyone. KV stays the hot read path (it's at the edge, Postgres isn't); this
-- table exists so a course can be listed, joined against progress, and survive
-- the 90-day KV TTL.

CREATE TABLE IF NOT EXISTS courses (
    topic_hash TEXT        PRIMARY KEY,
    topic      TEXT        NOT NULL,
    depth      TEXT        NOT NULL,
    payload    JSONB       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- user_courses
-- ---------------------------------------------------------------------------
-- The join: which courses a person has started. The free-tier "1 course" limit
-- counts rows here, which is why it now survives a reinstall — the old in-memory
-- version reset every time the app died.

CREATE TABLE IF NOT EXISTS user_courses (
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_hash   TEXT        NOT NULL REFERENCES courses(topic_hash) ON DELETE CASCADE,
    started_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, topic_hash)
);

-- Ordering the home screen by recency is the only read this table serves.
CREATE INDEX IF NOT EXISTS user_courses_recent_idx
    ON user_courses (user_id, last_seen_at DESC);

-- ---------------------------------------------------------------------------
-- lesson_progress
-- ---------------------------------------------------------------------------
-- One row per lesson attempted. state mirrors the Kotlin LessonState enum —
-- LOCKED is never written, since a locked lesson is the absence of a row.

CREATE TABLE IF NOT EXISTS lesson_progress (
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_hash   TEXT        NOT NULL,
    lesson_id    TEXT        NOT NULL,
    state        TEXT        NOT NULL CHECK (state IN ('AVAILABLE', 'COMPLETE', 'NEEDS_REVIEW')),
    correct      INT         NOT NULL DEFAULT 0,
    total        INT         NOT NULL DEFAULT 0,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, topic_hash, lesson_id)
);

-- ---------------------------------------------------------------------------
-- daily_usage
-- ---------------------------------------------------------------------------
-- Free-tier limits, enforced server-side. This is the reason the whole table
-- exists: a client-side counter is a suggestion, and the first person to clear
-- app data gets unlimited generations. Generation costs real money.
--
-- `day` is a DATE in UTC. The client's local midnight will differ, which means
-- someone in IST gets their reset at 5:30am rather than midnight. Acceptable —
-- trusting a client-supplied date is how you get an infinite quota by changing
-- the device clock.

CREATE TABLE IF NOT EXISTS daily_usage (
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    day         DATE   NOT NULL,
    lessons     INT    NOT NULL DEFAULT 0,
    generations INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day)
);
