use testcontainers to build bigdata test kit
tech stack:
hive, hms(postgresql) apache/hive:3.1.3
cloudera-hms ghcr.io/openprojectx/cloudera-hms:0.1.15 referece /data/Git/cloudera-hms/README.md
hdfs  apache/hadoop:3.5.0

kafka apache/kafka:4.1.2 , schema registry, kafka ui, reference /data/Git/kafka-ui/documentation/compose/ui-kerberos.yaml
localstack s3 localstack/localstack:4.14.0
fakegcs fsouza/fake-gcs-server:1.54

support optional kerberos for each if applicable, openprojectx/kerby-kdc:latest
support optional tls, custom cert

highly configurable, composable, pluggable,

support spring boot starter for spring local development, 
add junit5 module 