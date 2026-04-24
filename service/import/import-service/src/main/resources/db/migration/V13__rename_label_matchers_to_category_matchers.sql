UPDATE import_configuration
SET matchers = (
    matchers - 'labelMatchers'
    || jsonb_build_object(
        'categoryMatchers',
        (
            SELECT COALESCE(jsonb_agg(
                elem - 'label' || jsonb_build_object('category', elem -> 'label')
            ), '[]'::jsonb)
            FROM jsonb_array_elements(matchers -> 'labelMatchers') AS elem
        )
    )
)
WHERE matchers ? 'labelMatchers';
