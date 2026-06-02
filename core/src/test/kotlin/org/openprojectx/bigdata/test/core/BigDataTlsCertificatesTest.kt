package org.openprojectx.bigdata.test.core

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BigDataTlsCertificatesTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `ensureCertificateAuthority creates missing certificate and key`() {
        val certificatePath = tempDir.resolve("root-ca.crt")
        val privateKeyPath = tempDir.resolve("root-ca.key")

        val files = BigDataTlsCertificates.ensureCertificateAuthority(certificatePath, privateKeyPath)

        assertEquals(certificatePath, files.certificatePath)
        assertEquals(privateKeyPath, files.privateKeyPath)
        assertTrue(Files.exists(certificatePath))
        assertTrue(Files.exists(privateKeyPath))
        assertTrue(Files.readString(certificatePath).contains("BEGIN CERTIFICATE"))
        assertTrue(Files.readString(privateKeyPath).contains("BEGIN"))
    }

    @Test
    fun `ensureCertificateAuthority reuses existing certificate and key`() {
        val certificatePath = tempDir.resolve("root-ca.crt")
        val privateKeyPath = tempDir.resolve("root-ca.key")
        BigDataTlsCertificates.ensureCertificateAuthority(certificatePath, privateKeyPath)
        val originalCertificate = Files.readString(certificatePath)
        val originalPrivateKey = Files.readString(privateKeyPath)

        val files = BigDataTlsCertificates.ensureCertificateAuthority(certificatePath, privateKeyPath)

        assertEquals(certificatePath, files.certificatePath)
        assertEquals(privateKeyPath, files.privateKeyPath)
        assertEquals(originalCertificate, Files.readString(certificatePath))
        assertEquals(originalPrivateKey, Files.readString(privateKeyPath))
    }

    @Test
    fun `ensureCertificateAuthority fails when only certificate exists`() {
        val certificatePath = tempDir.resolve("root-ca.crt")
        val privateKeyPath = tempDir.resolve("root-ca.key")
        Files.writeString(certificatePath, "not a real certificate")

        assertThrows(IllegalStateException::class.java) {
            BigDataTlsCertificates.ensureCertificateAuthority(certificatePath, privateKeyPath)
        }
    }

    @Test
    fun `generateTrustStore writes jvm truststore with certificate alias`() {
        val certificatePath = tempDir.resolve("root-ca.crt")
        val privateKeyPath = tempDir.resolve("root-ca.key")
        val trustStorePath = tempDir.resolve("truststore.p12")
        BigDataTlsCertificates.ensureCertificateAuthority(certificatePath, privateKeyPath)

        val trustStore = BigDataTlsCertificates.generateTrustStore(
            certificatePath = certificatePath,
            trustStorePath = trustStorePath,
            password = "secret",
            alias = "test-ca",
        )

        assertEquals(trustStorePath, trustStore.path)
        assertEquals("PKCS12", trustStore.type)
        assertTrue(Files.exists(trustStorePath))
        loadKeyStore(trustStorePath, "secret", "PKCS12").also {
            assertTrue(it.containsAlias("test-ca"))
            assertNotNull(it.getCertificate("test-ca"))
        }
    }

    @Test
    fun `installCertificateAuthorityToJavaTrustStore imports certificate and creates backup`() {
        val certificatePath = tempDir.resolve("root-ca.crt")
        val privateKeyPath = tempDir.resolve("root-ca.key")
        val javaHome = tempDir.resolve("jdk")
        val cacertsPath = javaHome.resolve("lib").resolve("security").resolve("cacerts")
        BigDataTlsCertificates.ensureCertificateAuthority(certificatePath, privateKeyPath)
        createEmptyKeyStore(cacertsPath, "changeit", "PKCS12")

        val result = BigDataTlsCertificates.installCertificateAuthorityToJavaTrustStore(
            javaHome = javaHome,
            certificatePath = certificatePath,
            alias = "test-ca",
            type = "PKCS12",
        )

        assertEquals(cacertsPath, result.trustStorePath)
        assertEquals("test-ca", result.alias)
        val backupPath = result.backupPath ?: error("Expected a cacerts backup")
        assertNotNull(backupPath)
        assertTrue(Files.exists(backupPath))
        loadKeyStore(cacertsPath, "changeit", "PKCS12").also {
            assertTrue(it.containsAlias("test-ca"))
            assertNotNull(it.getCertificate("test-ca"))
        }
    }

    private fun createEmptyKeyStore(path: Path, password: String, type: String) {
        path.parent?.let { Files.createDirectories(it) }
        val keyStore = KeyStore.getInstance(type)
        keyStore.load(null, password.toCharArray())
        Files.newOutputStream(path).use { keyStore.store(it, password.toCharArray()) }
    }

    private fun loadKeyStore(path: Path, password: String, type: String): KeyStore {
        val keyStore = KeyStore.getInstance(type)
        Files.newInputStream(path).use { keyStore.load(it, password.toCharArray()) }
        return keyStore
    }
}
