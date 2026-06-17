-- Optional user avatar image URL. When null, the UI renders initials.
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url TEXT;
