--------------------------------------------------------
-- Eliminación de índices trigram redundantes
--------------------------------------------------------

DROP INDEX IF EXISTS public.idx_books_title_trgm;

DROP INDEX IF EXISTS public.idx_authors_name_trgm;

DROP INDEX IF EXISTS public.idx_publishers_name_trgm;

--------------------------------------------------------
-- ProviderBook
--------------------------------------------------------

DROP INDEX IF EXISTS public.idx_provider_books_identifier_status;

CREATE INDEX idx_provider_books_identifier_issues
    ON public.provider_books (identifier_status)
    WHERE identifier_status IS DISTINCT FROM 'VALID_ISBN';

ALTER TABLE public.provider_books
    DROP CONSTRAINT IF EXISTS uk_provider_books_external_code;

CREATE UNIQUE INDEX uk_provider_books_external_code
    ON public.provider_books (provider_id, external_code)
    WHERE external_code IS NOT NULL;