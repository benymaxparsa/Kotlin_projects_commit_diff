/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.internal.utils.cryptor

import net.mamoe.mirai.internal.utils.MiraiPlatformUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement


@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias ECDHPrivateKey = PrivateKey
@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias ECDHPublicKey = PublicKey

internal actual class ECDHKeyPairImpl(
    private val delegate: KeyPair
) : ECDHKeyPair {
    override val privateKey: ECDHPrivateKey get() = delegate.private
    override val publicKey: ECDHPublicKey get() = delegate.public

    override val initialShareKey: ByteArray =
        ECDH.calculateShareKey(privateKey, initialPublicKey)
}

@Suppress("FunctionName")
internal actual fun ECDH() =
    ECDH(ECDH.generateKeyPair())

internal actual class ECDH actual constructor(actual val keyPair: ECDHKeyPair) {
    actual companion object {
        actual val isECDHAvailable: Boolean

        init {
            isECDHAvailable = kotlin.runCatching {
                fun testECDH() {
                    ECDHKeyPairImpl(
                        KeyPairGenerator.getInstance("ECDH")
                            .also { it.initialize(ECGenParameterSpec("secp192k1")) }
                            .genKeyPair()).let {
                        calculateShareKey(it.privateKey, it.publicKey)
                    }
                }

                if (kotlin.runCatching { testECDH() }.isSuccess) {
                    return@runCatching
                }

                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) != null) {
                    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                }
                Security.addProvider(BouncyCastleProvider())
                testECDH()
            }.isSuccess
        }

        actual fun generateKeyPair(): ECDHKeyPair {
            if (!isECDHAvailable) {
                return ECDHKeyPair.DefaultStub
            }
            return ECDHKeyPairImpl(KeyPairGenerator.getInstance("ECDH")
                .also { it.initialize(ECGenParameterSpec("secp192k1")) }
                .genKeyPair())
        }

        actual fun calculateShareKey(
            privateKey: ECDHPrivateKey,
            publicKey: ECDHPublicKey
        ): ByteArray {
            val instance = KeyAgreement.getInstance("ECDH", "BC")
            instance.init(privateKey)
            instance.doPhase(publicKey, true)
            return MiraiPlatformUtils.md5(instance.generateSecret())
        }

        actual fun constructPublicKey(key: ByteArray): ECDHPublicKey {
            return KeyFactory.getInstance("EC", "BC").generatePublic(X509EncodedKeySpec(key))
        }
    }

    actual fun calculateShareKeyByPeerPublicKey(peerPublicKey: ECDHPublicKey): ByteArray {
        return calculateShareKey(keyPair.privateKey, peerPublicKey)
    }

    actual override fun toString(): String {
        return "ECDH(keyPair=$keyPair)"
    }
}