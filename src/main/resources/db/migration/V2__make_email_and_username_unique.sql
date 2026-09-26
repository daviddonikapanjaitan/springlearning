-- Email and username must be unique across all users, including soft-deleted ones.
-- Username uniqueness is case-insensitive ('David' and 'david' are the same user).
-- Emails are already lowercased by the application, but the index enforces it regardless.
DROP INDEX IF EXISTS ux_users_email_active;
DROP INDEX IF EXISTS ux_users_username_active;

CREATE UNIQUE INDEX ux_users_email    ON users (lower(email));
CREATE UNIQUE INDEX ux_users_username ON users (lower(username));
