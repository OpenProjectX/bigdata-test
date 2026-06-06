package org.openprojectx.bigdata.test.example.spark

import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsBuilder
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsConfigurer
import org.openprojectx.bigdata.test.extensions.junit5.BigDataExtensions
import org.openprojectx.bigdata.test.junit5.BigDataTest

@BigDataExtensions("classpath:spark-bigdata-extensions.toml")
@BigDataTest(
    config = [
        "classpath:spark-bigdata-test-common.toml",
        "classpath:spark-bigdata-test-cloudera-hms-kerberos.toml",
    ],
)
class SparkBigDataTestExample : SparkBigDataScenario() {
    override val runId: String get() = scenarioRunId
    override val s3BucketExtensionId: String get() = S3_BUCKET_ID
    override val gcsBucketExtensionId: String get() = GCS_BUCKET_ID
    override val sparkSqlPrepExtensionId: String get() = SPARK_SQL_PREP_ID

    companion object : BigDataExtensionsConfigurer {
        private val scenarioRunId = System.nanoTime().toString()
        private const val S3_BUCKET_ID = "spark-s3-bucket"
        private const val GCS_BUCKET_ID = "spark-gcs-bucket"
        private const val SPARK_SQL_PREP_ID = "spark-sql-prep"

        override fun configure(extensions: BigDataExtensionsBuilder) {
            val s3Bucket = "spark-iceberg-s3-$scenarioRunId"
            val gcsBucket = "spark-iceberg-gcs-$scenarioRunId"
            val namespace = "demo_$scenarioRunId"
            val gcsDataPath = "gs://$gcsBucket/spark-sql-prep/data/events"

            extensions.s3Bucket(bucket = s3Bucket, id = S3_BUCKET_ID)
            extensions.gcsBucket(bucket = gcsBucket, id = GCS_BUCKET_ID)
            extensions.sparkSqlPreparation {
                id = SPARK_SQL_PREP_ID
                config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
                config("spark.sql.catalog.prep_s3", "org.apache.iceberg.spark.SparkCatalog")
                config("spark.sql.catalog.prep_s3.type", "hadoop")
                config("spark.sql.catalog.prep_s3.warehouse", "s3a://$s3Bucket/spark-sql-prep/warehouse")
                config("spark.hadoop.fs.gs.max.requests.per.batch", "1")
                config("spark.hadoop.fs.gs.operation.move.enable", "false")
                config("spark.hadoop.fs.gs.copy.with.rewrite.enable", "false")
                statement(
                    """
                    CREATE DATABASE IF NOT EXISTS prep_hms_$scenarioRunId
                    LOCATION 'hdfs://hdfs:8020/user/hive/warehouse/prep_hms_$scenarioRunId.db'
                    """.trimIndent()
                )
                statement("CREATE NAMESPACE IF NOT EXISTS prep_s3.$namespace")
                statement(
                    """
                    CREATE OR REPLACE TABLE prep_s3.$namespace.events
                    USING iceberg
                    AS SELECT * FROM VALUES
                        (1, 'alpha', 'prep-s3'),
                        (2, 'beta', 'prep-s3')
                        AS events(id, name, storage)
                    """.trimIndent()
                )
                statement(
                    """
                    INSERT OVERWRITE DIRECTORY '$gcsDataPath'
                    USING parquet
                    SELECT * FROM VALUES
                        (1, 'alpha', 'prep-gcs'),
                        (2, 'beta', 'prep-gcs')
                        AS events(id, name, storage)
                    """.trimIndent()
                )
            }
        }
    }
}
