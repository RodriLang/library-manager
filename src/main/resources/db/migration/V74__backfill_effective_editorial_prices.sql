WITH source_prices AS (SELECT ep.book_id,
                              ep.valid_from,
                              MIN(ep.price)                           AS agreed_price,
                              MIN(ep.currency)                        AS currency,
                              COUNT(*)                                AS source_count,
                              COUNT(DISTINCT (ep.price, ep.currency)) AS distinct_values,
                              CASE
                                  WHEN COUNT(*) = 1 THEN MIN(ep.id)
                                  ELSE NULL
                                  END                                 AS selected_editorial_price_id
                       FROM editorial_prices ep
                       WHERE ep.active = TRUE
                         AND ep.origin IN (
                                           'PRICE_LIST',
                                           'MANUAL_DISTRIBUTOR',
                                           'MANUAL_PUBLISHER'
                           )
                         AND ep.valid_from IS NOT NULL
                       GROUP BY ep.book_id,
                                ep.valid_from),
     unambiguous AS (SELECT book_id,
                            valid_from,
                            agreed_price,
                            currency,
                            source_count,
                            selected_editorial_price_id
                     FROM source_prices
                     WHERE distinct_values = 1),
     ordered AS (SELECT unambiguous.*,

                        LAG(agreed_price) OVER (
                            PARTITION BY book_id
                            ORDER BY valid_from
                            ) AS previous_price,

                        LAG(currency) OVER (
                            PARTITION BY book_id
                            ORDER BY valid_from
                            ) AS previous_currency
                 FROM unambiguous),
     price_changes AS (SELECT *
                       FROM ordered
                       WHERE previous_price IS NULL
                          OR previous_price IS DISTINCT FROM agreed_price
                          OR previous_currency IS DISTINCT FROM currency)
INSERT
INTO effective_editorial_prices (book_id,
                                 price,
                                 currency,
                                 valid_from,
                                 determination_type,
                                 authority,
                                 selected_editorial_price_id,
                                 active)
SELECT book_id,
       agreed_price,
       currency,
       valid_from,
       CASE
           WHEN source_count = 1
               THEN 'AUTO_SINGLE_SOURCE'
           ELSE 'AUTO_SOURCE_AGREEMENT'
           END,
       'OFFICIAL',
       selected_editorial_price_id,
       TRUE
FROM price_changes;