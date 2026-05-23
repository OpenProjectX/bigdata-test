package org.openprojectx.bigdata.test.example.spring

import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean

@SpringBootApplication
class BigDataTestExampleApplication {
    @Bean
    fun printBigDataEndpoints(kitProvider: ObjectProvider<BigDataTestKit>): ApplicationRunner =
        ApplicationRunner {
            val kit = kitProvider.getIfAvailable()
            kit?.springProperties()?.forEach { (key, value) ->
                println("$key=$value")
            }
        }
}

fun main(args: Array<String>) {
    runApplication<BigDataTestExampleApplication>(*args)
}
