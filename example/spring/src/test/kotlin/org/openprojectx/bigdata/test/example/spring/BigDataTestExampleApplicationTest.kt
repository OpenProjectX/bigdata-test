package org.openprojectx.bigdata.test.example.spring

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = [
        "bigdata.test.enabled=false",
    ],
)
class BigDataTestExampleApplicationTest {
    @Test
    fun contextLoads() {
    }
}
