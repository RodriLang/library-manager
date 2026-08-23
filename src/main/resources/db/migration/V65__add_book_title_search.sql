ALTER TABLE books
    ADD COLUMN title_search VARCHAR(255);

UPDATE books b
SET title_search = normalized.title_search
FROM (
         SELECT
             source.id,
             COALESCE(
                     NULLIF(
                                     string_agg(source.token, ' ' ORDER BY source.ord)
                                     FILTER (
                                         WHERE source.token NOT IN (
                                                                    'el',
                                                                    'la',
                                                                    'los',
                                                                    'las',
                                                                    'un',
                                                                    'una',
                                                                    'unos',
                                                                    'unas',
                                                                    'de',
                                                                    'del',
                                                                    'al',
                                                                    'y',
                                                                    'e'
                                         )
                                         ),
                                     ''
                     ),
                     source.normalized_title
             ) AS title_search
         FROM (
                  SELECT
                      b.id,
                      regexp_replace(
                              trim(
                                      regexp_replace(
                                              immutable_unaccent(lower(b.title)),
                                              '[^[:alnum:][:space:]]',
                                              ' ',
                                              'g'
                                      )
                              ),
                              '[[:space:]]+',
                              ' ',
                              'g'
                      ) AS normalized_title,
                      token,
                      ord
                  FROM books b
                           CROSS JOIN LATERAL regexp_split_to_table(
                          regexp_replace(
                                  trim(
                                          regexp_replace(
                                                  immutable_unaccent(lower(b.title)),
                                                  '[^[:alnum:][:space:]]',
                                                  ' ',
                                                  'g'
                                          )
                                  ),
                                  '[[:space:]]+',
                                  ' ',
                                  'g'
                          ),
                          '[[:space:]]+'
                                              ) WITH ORDINALITY AS tokens(token, ord)
              ) source
         GROUP BY source.id, source.normalized_title
     ) normalized
WHERE b.id = normalized.id;

ALTER TABLE books
    ALTER COLUMN title_search SET NOT NULL;

CREATE INDEX idx_books_title_search_fts
    ON books
        USING gin (to_tsvector('simple', title_search));