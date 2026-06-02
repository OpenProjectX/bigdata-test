package org.openprojectx.bigdata.test.core.container

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import org.bouncycastle.asn1.x500.X500Name
import org.openprojectx.bigdata.test.core.BigDataTlsCertificates
import org.openprojectx.bigdata.test.core.TlsOptions

internal class TlsMaterial(
    private val options: TlsOptions,
) {
    private val directory: Path = Files.createTempDirectory("bigdata-test-tls-")
    private val caCertPath: Path = directory.resolve("ca.crt")
    private val caKeyPath: Path = directory.resolve("ca.key")
    private val ca: CertificateKeyPair = loadOrCreateCa()
    val trustStorePath: Path = options.trustStorePath?.let { Path.of(it) } ?: directory.resolve("truststore.p12")
    val trustStorePassword: String = options.trustStorePassword

    init {
        writePem(caCertPath, ca.certificate)
        writePem(caKeyPath, ca.privateKey)
        writeTrustStore()
    }

    fun haproxyPem(name: String, domain: String): Path {
        val keyPair = BigDataTlsCertificates.rsaKeyPair()
        val cert = BigDataTlsCertificates.signedCertificate(
            subject = X500Name("CN=$domain"),
            subjectKeyPair = keyPair,
            issuer = X500Name(ca.certificate.subjectX500Principal.name),
            issuerKey = ca.privateKey,
            issuerCert = ca.certificate,
            sanDomains = listOf(domain, "localhost"),
        )
        val pem = directory.resolve("${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}.pem")
        Files.writeString(
            pem,
            BigDataTlsCertificates.toPem(cert) + BigDataTlsCertificates.toPem(keyPair.private),
        )
        return pem
    }

    fun keyStore(name: String, domain: String, sanDomains: List<String> = emptyList()): KeyStoreMaterial {
        val keyPair = BigDataTlsCertificates.rsaKeyPair()
        val cert = BigDataTlsCertificates.signedCertificate(
            subject = X500Name("CN=$domain"),
            subjectKeyPair = keyPair,
            issuer = X500Name(ca.certificate.subjectX500Principal.name),
            issuerKey = ca.privateKey,
            issuerCert = ca.certificate,
            sanDomains = (listOf(domain, "localhost") + sanDomains).distinct(),
        )
        val path = directory.resolve("${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}.p12")
        val password = options.trustStorePassword
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, password.toCharArray())
        keyStore.setKeyEntry(
            "bigdata-test-$name",
            keyPair.private,
            password.toCharArray(),
            arrayOf(cert, ca.certificate),
        )
        Files.newOutputStream(path).use { output ->
            keyStore.store(output, password.toCharArray())
        }
        return KeyStoreMaterial(path = path, password = password, type = "PKCS12")
    }

    fun properties(): Map<String, String> =
        mapOf(
            "javax.net.ssl.trustStore" to trustStorePath.toString(),
            "javax.net.ssl.trustStorePassword" to trustStorePassword,
            "javax.net.ssl.trustStoreType" to "PKCS12",
            "bigdata.test.tls.ca-cert" to caCertPath.toString(),
        )

    private fun loadOrCreateCa(): CertificateKeyPair {
        val configuredCaCert = options.caCertPath ?: options.certPath
        val configuredCaKey = options.caKeyPath ?: options.keyPath
        return if (!configuredCaCert.isNullOrBlank() && !configuredCaKey.isNullOrBlank()) {
            CertificateKeyPair(
                certificate = BigDataTlsCertificates.readCertificate(Path.of(configuredCaCert)),
                privateKey = BigDataTlsCertificates.readPrivateKey(Path.of(configuredCaKey)),
            )
        } else {
            val keyPair = BigDataTlsCertificates.rsaKeyPair()
            val cert = BigDataTlsCertificates.selfSignedCaCertificate(keyPair)
            CertificateKeyPair(cert, keyPair.private)
        }
    }

    private fun writeTrustStore() {
        trustStorePath.parent?.let { Files.createDirectories(it) }
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, trustStorePassword.toCharArray())
        keyStore.setCertificateEntry("bigdata-test-root-ca", ca.certificate)
        Files.newOutputStream(trustStorePath).use { output ->
            keyStore.store(output, trustStorePassword.toCharArray())
        }
    }

    private fun writePem(path: Path, value: Any) {
        BigDataTlsCertificates.writePem(path, value)
    }

    private data class CertificateKeyPair(
        val certificate: X509Certificate,
        val privateKey: PrivateKey,
    )

    data class KeyStoreMaterial(
        val path: Path,
        val password: String,
        val type: String,
    )
}
