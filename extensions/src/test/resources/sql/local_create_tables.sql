CREATE OR REPLACE TEMP VIEW script_events AS
SELECT * FROM VALUES
    (1, 'alpha', 'script-resource'),
    (2, 'beta', 'script-resource')
    AS events(id, name, storage);

SELECT COUNT(*) FROM script_events WHERE storage = 'script-resource';
