ALTER TABLE authors
    DROP CONSTRAINT IF EXISTS uk_authors_name;

ALTER TABLE publishers
    DROP CONSTRAINT IF EXISTS uk_publishers_name;