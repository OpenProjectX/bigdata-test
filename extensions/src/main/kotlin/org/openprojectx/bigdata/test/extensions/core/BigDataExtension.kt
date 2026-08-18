package org.openprojectx.bigdata.test.extensions.core

import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.DEFAULT_SERVICE_INSTANCE
import org.openprojectx.bigdata.test.core.BigDataServiceId

interface BigDataExtension {
    val id: String
    val instance: String get() = DEFAULT_SERVICE_INSTANCE
    val requiredServices: Set<BigDataService> get() = emptySet()
    val requiredServiceInstances: Set<BigDataServiceId>
        get() = requiredServices.mapTo(linkedSetOf()) { BigDataServiceId(it, instance) }
    val events: Set<BigDataExtensionEvent> get() = setOf(BigDataExtensionEvent.AFTER_KIT_START)

    fun onEvent(event: BigDataExtensionEvent, context: BigDataExtensionContext) {
    }
}
