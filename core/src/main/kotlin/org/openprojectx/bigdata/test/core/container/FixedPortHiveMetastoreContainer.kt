package org.openprojectx.bigdata.test.core.container

import org.openprojectx.hive.docker.testcontainers.HiveMetastoreContainer
import org.testcontainers.containers.InternetProtocol
import org.testcontainers.utility.DockerImageName

internal class FixedPortHiveMetastoreContainer(image: String) :
    HiveMetastoreContainer(DockerImageName.parse(image)) {
    fun withServicePort(containerPort: Int, hostPort: Int = 0): FixedPortHiveMetastoreContainer =
        apply {
            require(hostPort >= 0) { "Host port must be 0 for random binding or a positive fixed port" }
            if (hostPort > 0) {
                addFixedExposedPort(hostPort, containerPort, InternetProtocol.TCP)
                addExposedPort(containerPort)
            }
        }
}
