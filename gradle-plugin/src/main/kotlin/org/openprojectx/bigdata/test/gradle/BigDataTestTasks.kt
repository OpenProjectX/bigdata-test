package org.openprojectx.bigdata.test.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Starts external Docker containers and has no cacheable outputs.")
abstract class BigDataTestStartTask : DefaultTask() {
    @get:Internal
    abstract val kitService: Property<BigDataTestGradleService>

    @TaskAction
    fun start() {
        val properties = kitService.get().startIfNeeded()
        logger.lifecycle("bigdata-test started; injected ${properties.size} properties")
    }
}

@DisableCachingByDefault(because = "Starts external Docker containers and waits for user interruption.")
abstract class BigDataTestRunTask : DefaultTask() {
    @get:Internal
    abstract val kitService: Property<BigDataTestGradleService>

    @TaskAction
    fun run() {
        val properties = kitService.get().startIfNeeded()
        logger.lifecycle("bigdata-test started; injected ${properties.size} properties")
        logger.lifecycle("bigdata-test is running. Press Ctrl+C to stop the Gradle process and close containers.")
        try {
            while (!Thread.currentThread().isInterrupted) {
                Thread.sleep(1_000)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}

@DisableCachingByDefault(because = "Stops external Docker containers and has no cacheable outputs.")
abstract class BigDataTestStopTask : DefaultTask() {
    @get:Internal
    abstract val kitService: Property<BigDataTestGradleService>

    @TaskAction
    fun stop() {
        kitService.get().close()
        logger.lifecycle("bigdata-test stopped")
    }
}
