package org.openprojectx.bigdata.test.core.container

import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

internal class GenericBigDataContainer(image: String) :
    GenericContainer<GenericBigDataContainer>(DockerImageName.parse(image))
