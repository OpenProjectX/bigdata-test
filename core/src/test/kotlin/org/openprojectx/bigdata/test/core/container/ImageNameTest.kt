package org.openprojectx.bigdata.test.core.container

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageNameTest {
    @Test
    fun `matches repository with registry port and tag`() {
        assertTrue("registry.example:5000/floci/floci:latest".isImageNamed("floci/floci"))
    }

    @Test
    fun `matches repository with registry port and digest`() {
        assertTrue("registry.example:5000/floci/floci@sha256:abcdef".isImageNamed("floci/floci"))
    }

    @Test
    fun `matches unqualified repository and tag`() {
        assertTrue("floci/floci-gcp:latest".isImageNamed("floci/floci-gcp"))
    }

    @Test
    fun `does not match a repository suffix in the image name`() {
        assertFalse("registry.example:5000/custom-floci/floci:latest".isImageNamed("floci/floci"))
    }
}
