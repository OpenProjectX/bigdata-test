package org.openprojectx.bigdata.test.extensions.core

import org.openprojectx.bigdata.test.core.BigDataService

interface BigDataExtension {
    val id: String
    val requiredServices: Set<BigDataService> get() = emptySet()
    val events: Set<BigDataExtensionEvent> get() = setOf(BigDataExtensionEvent.AFTER_KIT_START)

    fun onEvent(event: BigDataExtensionEvent, context: BigDataExtensionContext) {
    }
}
