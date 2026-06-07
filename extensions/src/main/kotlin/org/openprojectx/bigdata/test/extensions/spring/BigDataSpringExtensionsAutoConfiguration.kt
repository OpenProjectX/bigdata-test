package org.openprojectx.bigdata.test.extensions.spring

import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsConfigLoader
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionEvent
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionResourceLoader
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionResult
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionRunner
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
@AutoConfigureAfter(name = ["org.openprojectx.bigdata.test.autoconfigure.BigDataTestAutoConfiguration"])
@EnableConfigurationProperties(BigDataSpringExtensionsProperties::class)
@ConditionalOnProperty(prefix = "bigdata.extensions", name = ["enabled"], havingValue = "true")
class BigDataSpringExtensionsAutoConfiguration {
    @Bean(BIG_DATA_SPRING_EXTENSIONS_BEAN_NAME)
    @ConditionalOnBean(BigDataTestKit::class)
    @ConditionalOnMissingBean
    fun bigDataSpringExtensions(
        kit: BigDataTestKit,
        properties: BigDataSpringExtensionsProperties,
    ): BigDataSpringExtensions =
        BigDataSpringExtensions(kit, properties)

    companion object {
        @Bean
        @JvmStatic
        fun bigDataSpringExtensionsDependsOnPostProcessor(): BeanFactoryPostProcessor =
            BigDataSpringExtensionsDependsOnPostProcessor()
    }
}

@ConfigurationProperties("bigdata.extensions")
data class BigDataSpringExtensionsProperties(
    var enabled: Boolean = false,
    var config: List<String> = emptyList(),
    var configReplace: Boolean = false,
)

class BigDataSpringExtensions(
    private val kit: BigDataTestKit,
    private val properties: BigDataSpringExtensionsProperties,
) : InitializingBean, DisposableBean {
    private val resources = BigDataExtensionResourceLoader(Thread.currentThread().contextClassLoader)
    private var runner: BigDataExtensionRunner? = null
    private var currentResult: BigDataExtensionResult = BigDataExtensionResult(emptyMap())

    val result: BigDataExtensionResult
        get() = currentResult

    override fun afterPropertiesSet() {
        val extensions = BigDataExtensionsConfigLoader(resources).load(configLocations())
        runner = BigDataExtensionRunner(extensions, resources)
        currentResult = runner?.fire(BigDataExtensionEvent.AFTER_KIT_START, kit) ?: BigDataExtensionResult(emptyMap())
    }

    override fun destroy() {
        runner?.let { currentResult = it.fire(BigDataExtensionEvent.AFTER_ALL, kit, currentResult) }
    }

    private fun configLocations(): List<String> {
        val taskConfig = systemPropertyLocations(EXTENSIONS_CONFIG_PROPERTY)
        return if (properties.configReplace || System.getProperty(EXTENSIONS_CONFIG_REPLACE_PROPERTY).toBoolean()) {
            taskConfig.ifEmpty { properties.config }
        } else {
            properties.config + taskConfig
        }
    }

    private fun systemPropertyLocations(name: String): List<String> =
        System.getProperty(name)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()

    private companion object {
        const val EXTENSIONS_CONFIG_PROPERTY = "bigdata.extensions.config"
        const val EXTENSIONS_CONFIG_REPLACE_PROPERTY = "bigdata.extensions.config.replace"
    }
}

private class BigDataSpringExtensionsDependsOnPostProcessor : BeanFactoryPostProcessor {
    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        beanFactory.beanDefinitionNames.forEach { beanName ->
            val definition = beanFactory.getBeanDefinition(beanName)
            if (definition.shouldDependOnExtensions(beanName)) {
                val dependsOn = (definition.dependsOn.orEmpty().toList() + BIG_DATA_SPRING_EXTENSIONS_BEAN_NAME)
                    .distinct()
                    .toTypedArray()
                definition.setDependsOn(*dependsOn)
            }
        }
    }

    private fun BeanDefinition.shouldDependOnExtensions(beanName: String): Boolean {
        if (role != BeanDefinition.ROLE_APPLICATION) return false
        if (beanName in infrastructureBeanNames) return false
        if (beanName.startsWith("org.springframework.")) return false
        val className = beanClassName.orEmpty()
        if (className.isBlank()) return true
        return infrastructureClassPrefixes.none { className.startsWith(it) }
    }

    private companion object {
        val infrastructureBeanNames = setOf(
            BIG_DATA_SPRING_EXTENSIONS_BEAN_NAME,
            "bigDataSpringExtensionsDependsOnPostProcessor",
            "bigDataTestKit",
            "bigDataTestProperties",
            "bigDataSpringExtensionsProperties",
        )
        val infrastructureClassPrefixes = listOf(
            "org.openprojectx.bigdata.test.autoconfigure.",
            "org.openprojectx.bigdata.test.extensions.spring.",
            "org.openprojectx.bigdata.test.core.",
        )
    }
}

private const val BIG_DATA_SPRING_EXTENSIONS_BEAN_NAME = "bigDataSpringExtensions"
