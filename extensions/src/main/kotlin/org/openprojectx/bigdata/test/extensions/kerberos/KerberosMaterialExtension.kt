package org.openprojectx.bigdata.test.extensions.kerberos

import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.extensions.core.BigDataExtension
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionContext
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionEvent

data class KerberosMaterialExtension(
    override val id: String = "kerberos-material",
) : BigDataExtension {
    override val requiredServices: Set<BigDataService> = setOf(BigDataService.KERBEROS)
    override val events: Set<BigDataExtensionEvent> = setOf(BigDataExtensionEvent.AFTER_KIT_START)

    override fun onEvent(event: BigDataExtensionEvent, context: BigDataExtensionContext) {
        val kerberos = context.endpoint(BigDataService.KERBEROS)
        put(context, "realm", kerberos.property("bigdata.test.kerberos.realm"))
        put(context, "kdc", kerberos.property("bigdata.test.kerberos.kdc"))
        put(context, "krb5-conf", kerberos.property("bigdata.test.kerberos.krb5-conf"))
        put(context, "client.principal", kerberos.property("bigdata.test.kerberos.client-principal"))
        put(context, "client.password", kerberos.property("bigdata.test.kerberos.client-password"))
        put(context, "client.keytab", kerberos.property("bigdata.test.kerberos.client-keytab"))
        put(context, "client.keytab.container", kerberos.property("bigdata.test.kerberos.client-keytab.container"))

        context.kit.endpoints().forEach { (service, endpoint) ->
            endpoint.properties.forEach { (key, value) ->
                if (key.endsWith(".kerberos.principal")) put(context, "${service.key}.principal", value)
                if (key.endsWith(".kerberos.service-name")) put(context, "${service.key}.service-name", value)
                if (key.endsWith(".kerberos.keytab")) put(context, "${service.key}.keytab.container", value)
                if (key.endsWith(".kerberos.keytab.local")) put(context, "${service.key}.keytab", value)
            }
        }
    }

    private fun put(context: BigDataExtensionContext, key: String, value: String) {
        context.putOutput("$id.$key", value)
    }

    private val BigDataService.key: String
        get() = name.lowercase().replace('_', '-')
}
