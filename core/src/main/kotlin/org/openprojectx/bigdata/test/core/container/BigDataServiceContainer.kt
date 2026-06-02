package org.openprojectx.bigdata.test.core.container

import org.openprojectx.bigdata.test.core.BigDataEndpoint
import org.openprojectx.bigdata.test.core.BigDataService
import org.testcontainers.containers.GenericContainer

internal data class BigDataServiceContainer(
    val service: BigDataService,
    val container: GenericContainer<*>,
    val afterStart: () -> Unit = {},
    val endpoint: () -> BigDataEndpoint,
)
