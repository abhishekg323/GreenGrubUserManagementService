-- FK pointer to image-service; nullable since not every user has a profile image.
-- The image bytes/URL live in image-service — we only persist the id here.
ALTER TABLE users ADD COLUMN IF NOT EXISTS image_id VARCHAR(36);
