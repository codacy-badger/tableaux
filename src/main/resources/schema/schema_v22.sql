ALTER TABLE system_columns
  ADD COLUMN frontend_read_only BOOLEAN DEFAULT 'FALSE' NOT NULL;
