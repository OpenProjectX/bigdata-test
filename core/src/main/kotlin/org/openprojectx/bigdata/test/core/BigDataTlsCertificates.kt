package org.openprojectx.bigdata.test.core

import java.io.StringReader
import java.io.StringWriter
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
import org.bouncycastle.asn1.x509.BasicConstraints
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

data class CertificateAuthorityFiles(
    val certificatePath: Path,
    val privateKeyPath: Path,
)

data class TrustStoreFile(
    val path: Path,
    val password: String,
    val type: String,
    val alias: String,
)

data class JavaTrustStoreInstallResult(
    val javaHome: Path,
    val trustStorePath: Path,
    val backupPath: Path?,
    val alias: String,
    val type: String,
)

object BigDataTlsCertificates {
    init {
        Security.addProvider(BouncyCastleProvider())
    }

    @JvmStatic
    @JvmOverloads
    fun generateCertificateAuthority(
        certificatePath: Path,
        privateKeyPath: Path,
        commonName: String = "bigdata-test-root-ca",
        validityDays: Long = 3650,
        overwrite: Boolean = false,
    ): CertificateAuthorityFiles {
        require(validityDays > 0) { "validityDays must be positive" }
        if (!overwrite) {
            require(!Files.exists(certificatePath)) { "CA certificate already exists: $certificatePath" }
            require(!Files.exists(privateKeyPath)) { "CA private key already exists: $privateKeyPath" }
        }

        certificatePath.parent?.let { Files.createDirectories(it) }
        privateKeyPath.parent?.let { Files.createDirectories(it) }

        val keyPair = rsaKeyPair()
        val certificate = selfSignedCaCertificate(keyPair, commonName, validityDays)
        writePem(certificatePath, certificate)
        writePem(privateKeyPath, keyPair.private)
        return CertificateAuthorityFiles(certificatePath, privateKeyPath)
    }

    @JvmStatic
    fun ensureCertificateAuthority(
        certificatePath: Path,
        privateKeyPath: Path,
    ): CertificateAuthorityFiles {
        val certExists = Files.exists(certificatePath)
        val keyExists = Files.exists(privateKeyPath)
        return when {
            certExists && keyExists -> CertificateAuthorityFiles(certificatePath, privateKeyPath)
            !certExists && !keyExists -> generateCertificateAuthority(certificatePath, privateKeyPath)
            else -> error("CA certificate and private key must either both exist or both be absent")
        }
    }

    @JvmStatic
    @JvmOverloads
    fun generateTrustStore(
        certificatePath: Path,
        trustStorePath: Path,
        password: String = "changeit",
        alias: String = "bigdata-test-root-ca",
        type: String = "PKCS12",
        overwrite: Boolean = false,
    ): TrustStoreFile {
        if (!overwrite) {
            require(!Files.exists(trustStorePath)) { "Truststore already exists: $trustStorePath" }
        }
        trustStorePath.parent?.let { Files.createDirectories(it) }
        val keyStore = KeyStore.getInstance(type)
        keyStore.load(null, password.toCharArray())
        keyStore.setCertificateEntry(alias, readCertificate(certificatePath))
        Files.newOutputStream(trustStorePath).use { output ->
            keyStore.store(output, password.toCharArray())
        }
        return TrustStoreFile(trustStorePath, password, type, alias)
    }

    @JvmStatic
    @JvmOverloads
    fun installCertificateAuthorityToJavaTrustStore(
        javaHome: Path,
        certificatePath: Path,
        storePassword: String = "changeit",
        alias: String = "bigdata-test-root-ca",
        overwriteAlias: Boolean = false,
        backup: Boolean = true,
        type: String = KeyStore.getDefaultType(),
    ): JavaTrustStoreInstallResult {
        val trustStorePath = javaHome.resolve("lib").resolve("security").resolve("cacerts")
        require(Files.isRegularFile(trustStorePath)) { "JDK truststore does not exist: $trustStorePath" }

        val password = storePassword.toCharArray()
        val (keyStore, resolvedType) = loadExistingKeyStore(trustStorePath, password, type)
        if (keyStore.containsAlias(alias) && !overwriteAlias) {
            error("JDK truststore already contains alias '$alias': $trustStorePath")
        }

        val backupPath = if (backup) {
            val path = trustStorePath.resolveSibling("cacerts.bigdata-test.bak")
            Files.copy(trustStorePath, path, StandardCopyOption.REPLACE_EXISTING)
            path
        } else {
            null
        }

        keyStore.setCertificateEntry(alias, readCertificate(certificatePath))
        Files.newOutputStream(trustStorePath).use { output -> keyStore.store(output, password) }
        return JavaTrustStoreInstallResult(
            javaHome = javaHome,
            trustStorePath = trustStorePath,
            backupPath = backupPath,
            alias = alias,
            type = resolvedType,
        )
    }

    private fun loadExistingKeyStore(
        path: Path,
        password: CharArray,
        preferredType: String,
    ): Pair<KeyStore, String> {
        val types = listOf(preferredType, "JKS", "PKCS12").distinct()
        var lastError: Exception? = null
        for (type in types) {
            val keyStore = KeyStore.getInstance(type)
            try {
                Files.newInputStream(path).use { input -> keyStore.load(input, password) }
                return keyStore to type
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IllegalStateException("Failed to load truststore $path as ${types.joinToString()}", lastError)
    }

    internal fun selfSignedCaCertificate(
        keyPair: KeyPair,
        commonName: String = "bigdata-test-root-ca",
        validityDays: Long = 3650,
    ): X509Certificate {
        val subject = X500Name("CN=$commonName")
        return signedCertificate(
            subject = subject,
            subjectKeyPair = keyPair,
            issuer = subject,
            issuerKey = keyPair.private,
            issuerCert = null,
            sanDomains = emptyList(),
            isCa = true,
            validityDays = validityDays,
        )
    }

    internal fun signedCertificate(
        subject: X500Name,
        subjectKeyPair: KeyPair,
        issuer: X500Name,
        issuerKey: PrivateKey,
        issuerCert: X509Certificate?,
        sanDomains: List<String>,
        isCa: Boolean = false,
        validityDays: Long = 3650,
    ): X509Certificate {
        val now = Instant.now().minus(1, ChronoUnit.DAYS)
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            BigInteger(160, SecureRandom()),
            Date.from(now),
            Date.from(now.plus(validityDays, ChronoUnit.DAYS)),
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
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        }
        val holder = builder.build(JcaContentSignerBuilder("SHA256withRSA").build(issuerKey))
        return JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(holder)
            .also { it.verify(if (issuerCert == null) subjectKeyPair.public else issuerCert.publicKey) }
    }

    internal fun rsaKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    internal fun readCertificate(path: Path): X509Certificate =
        PEMParser(StringReader(Files.readString(path))).use { parser ->
            val value = parser.readObject()
            require(value is X509CertificateHolder) { "Expected an X.509 PEM certificate in $path" }
            JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(value)
        }

    internal fun readPrivateKey(path: Path): PrivateKey =
        PEMParser(StringReader(Files.readString(path))).use { parser ->
            val value = parser.readObject()
            val converter = JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
            when (value) {
                is PEMKeyPair -> converter.getKeyPair(value).private
                is org.bouncycastle.asn1.pkcs.PrivateKeyInfo -> converter.getPrivateKey(value)
                else -> error("Expected a PEM private key in $path")
            }
        }

    internal fun writePem(path: Path, value: Any) {
        Files.writeString(path, toPem(value), StandardCharsets.UTF_8)
    }

    internal fun toPem(value: Any): String =
        StringWriter().use { buffer ->
            JcaPEMWriter(buffer).use { writer -> writer.writeObject(value) }
            buffer.toString()
        }
}
