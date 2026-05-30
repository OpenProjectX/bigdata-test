package org.openprojectx.bigdata.test.example.spark

import org.openprojectx.bigdata.test.core.ContainerLogMode
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsBuilder
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsConfigurer
import org.openprojectx.bigdata.test.extensions.junit5.BigDataExtensions
import org.openprojectx.bigdata.test.junit5.BigDataTest

@BigDataExtensions("classpath:spark-bigdata-extensions.toml")
@BigDataTest(
    hdfs = true,
    hiveMetastore = true,
    kafka = true,
    schemaRegistry = true,
    localStackS3 = true,
    fakeGcs = true,
    containerLogMode = ContainerLogMode.FILE,
)
class SparkBigDataTestExample : SparkBigDataScenario() {
    override val runId: String get() = scenarioRunId
    override val s3BucketExtensionId: String get() = S3_BUCKET_ID
    override val gcsBucketExtensionId: String get() = GCS_BUCKET_ID

    companion object : BigDataExtensionsConfigurer {
        private val scenarioRunId = System.nanoTime().toString()
        private const val S3_BUCKET_ID = "spark-s3-bucket"
        private const val GCS_BUCKET_ID = "spark-gcs-bucket"

        override fun configure(extensions: BigDataExtensionsBuilder) {
            extensions.s3Bucket(bucket = "spark-iceberg-s3-$scenarioRunId", id = S3_BUCKET_ID)
            extensions.gcsBucket(bucket = "spark-iceberg-gcs-$scenarioRunId", id = GCS_BUCKET_ID)
        }
    }
}
