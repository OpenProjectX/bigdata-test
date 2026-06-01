package org.openprojectx.bigdata.test.core.container

import java.io.StringReader
import java.io.StringWriter
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.openprojectx.bigdata.test.core.TlsOptions

internal class TlsMaterial(
    private val options: TlsOptions,
) {
    companion object {
        init {
            Security.addProvider(BouncyCastleProvider())
        }
    }

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
        val keyPair = rsaKeyPair()
        val cert = signedCertificate(
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
            toPem(cert) + toPem(keyPair.private),
            StandardCharsets.UTF_8,
        )
        return pem
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
                certificate = readCertificate(Path.of(configuredCaCert)),
                privateKey = readPrivateKey(Path.of(configuredCaKey)),
            )
        } else {
            val keyPair = rsaKeyPair()
            val cert = selfSignedCaCertificate(keyPair)
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

    private fun selfSignedCaCertificate(keyPair: KeyPair): X509Certificate {
        val subject = X500Name("CN=bigdata-test-root-ca")
        return signedCertificate(
            subject = subject,
            subjectKeyPair = keyPair,
            issuer = subject,
            issuerKey = keyPair.private,
            issuerCert = null,
            sanDomains = emptyList(),
            isCa = true,
        )
    }

    private fun signedCertificate(
        subject: X500Name,
        subjectKeyPair: KeyPair,
        issuer: X500Name,
        issuerKey: PrivateKey,
        issuerCert: X509Certificate?,
        sanDomains: List<String>,
        isCa: Boolean = false,
    ): X509Certificate {
        val now = Instant.now().minus(1, ChronoUnit.DAYS)
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            BigInteger(160, SecureRandom()),
            Date.from(now),
            Date.from(now.plus(3650, ChronoUnit.DAYS)),
            subject,
            subjectKeyPair.public,
        )
        val extensionUtils = JcaX509ExtensionUtils()
        builder.addExtension(Extension.subjectKeyIdentifier, false, extensionUtils.createSubjectKeyIdentifier(subjectKeyPair.public))
        issuerCert?.let {
            builder.addExtension(Extension.authorityKeyIdentifier, false, extensionUtils.createAuthorityKeyIdentifier(it.publicKey))
        }
        if (sanDomains.isNotEmpty()) {
            builder.addExtension(
                Extension.subjectAlternativeName,
                false,
                GeneralNames(sanDomains.map { GeneralName(GeneralName.dNSName, it) }.toTypedArray()),
            )
        }
        if (isCa) {
            builder.addExtension(Extension.basicConstraints, true, org.bouncycastle.asn1.x509.BasicConstraints(true))
        }
        val holder = builder.build(JcaContentSignerBuilder("SHA256withRSA").build(issuerKey))
        return JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(holder)
            .also { it.verify(if (issuerCert == null) subjectKeyPair.public else issuerCert.publicKey) }
    }

    private fun rsaKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun readCertificate(path: Path): X509Certificate =
        PEMParser(StringReader(Files.readString(path))).use { parser ->
            val value = parser.readObject()
            require(value is X509CertificateHolder) { "Expected an X.509 PEM certificate in $path" }
            JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(value)
        }

    private fun readPrivateKey(path: Path): PrivateKey =
        PEMParser(StringReader(Files.readString(path))).use { parser ->
            val value = parser.readObject()
            val converter = JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
            when (value) {
                is PEMKeyPair -> converter.getKeyPair(value).private
                is org.bouncycastle.asn1.pkcs.PrivateKeyInfo -> converter.getPrivateKey(value)
                else -> error("Expected a PEM private key in $path")
            }
        }

    private fun writePem(path: Path, value: Any) {
        Files.writeString(path, toPem(value), StandardCharsets.UTF_8)
    }

    private fun toPem(value: Any): String =
        StringWriter().use { buffer ->
            JcaPEMWriter(buffer).use { writer -> writer.writeObject(value) }
            buffer.toString()
        }

    private data class CertificateKeyPair(
        val certificate: X509Certificate,
        val privateKey: PrivateKey,
    )
}
