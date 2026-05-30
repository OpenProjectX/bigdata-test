package org.openprojectx.bigdata.test.extensions.config

interface BigDataExtensionsConfigurer {
    fun configure(extensions: BigDataExtensionsBuilder)
}

class NoopBigDataExtensionsConfigurer : BigDataExtensionsConfigurer {
    override fun configure(extensions: BigDataExtensionsBuilder) = Unit
}
