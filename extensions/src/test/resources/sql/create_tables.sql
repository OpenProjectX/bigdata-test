CREATE NAMESPACE IF NOT EXISTS prep_s3.demo;

CREATE TABLE IF NOT EXISTS prep_s3.demo.events_iceberg (
    id INT,
    name STRING,
    storage STRING
) USING iceberg;

INSERT INTO prep_s3.demo.events_iceberg
VALUES
    (1, 'alpha', 'iceberg-s3'),
    (2, 'beta', 'iceberg-s3');

CREATE TABLE IF NOT EXISTS demo.events_s3 (
    id INT,
    name STRING,
    storage STRING
) USING parquet
LOCATION 's3a://spark-sql-prep-s3/parquet/events';

INSERT INTO demo.events_s3
VALUES
    (1, 'alpha', 's3-parquet'),
    (2, 'beta', 's3-parquet');

INSERT OVERWRITE DIRECTORY 'gs://spark-sql-prep-gcs/parquet/events'
USING parquet
SELECT * FROM VALUES
    (1, 'alpha', 'gcs-parquet'),
    (2, 'beta', 'gcs-parquet')
    AS events(id, name, storage);
