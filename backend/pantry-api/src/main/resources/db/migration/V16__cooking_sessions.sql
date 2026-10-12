-- Cooking sessions: one row per time a user cooks a recipe, so H5 can deduct the
-- exact amounts on completion and later answer "how long did it take / did it finish".
--
-- status is a state machine (IN_PROGRESS, PAUSED, COMPLETED, ABANDONED) enforced by
-- the application, not by the schema: the transitions the code allows are the rule
-- that has to be explained to a future caller, and a CHECK could only list the states,
-- not who is allowed to move between them. What the schema does hold is that every row
-- has an owner, a recipe and a start, and that completion and abandonment remember
-- when they happened. completed_at is null while the session lives, so the duration of
-- a finished session is a subtraction rather than a state to keep in step.
--
-- Multiple sessions per recipe are allowed on purpose: a user may cook the same dish
-- twice in a week, and a "start again" button that had to kill the previous session
-- would lose the answers the metrics will want.
CREATE TABLE cooking_sessions
(
    id           BIGSERIAL     PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users (id),
    recipe_id    BIGINT        NOT NULL REFERENCES recipes (id),
    status       VARCHAR(30)   NOT NULL,
    started_at   TIMESTAMPTZ   NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_cooking_sessions_user ON cooking_sessions (user_id);