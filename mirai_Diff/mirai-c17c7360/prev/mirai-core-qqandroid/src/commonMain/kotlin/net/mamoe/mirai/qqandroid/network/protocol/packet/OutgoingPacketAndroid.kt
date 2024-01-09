package net.mamoe.mirai.qqandroid.network.protocol.packet


import kotlinx.io.core.BytePacketBuilder
import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.buildPacket
import kotlinx.io.core.writeFully
import net.mamoe.mirai.qqandroid.network.QQAndroidClient
import net.mamoe.mirai.utils.MiraiInternalAPI
import net.mamoe.mirai.utils.cryptor.DecrypterByteArray
import net.mamoe.mirai.utils.cryptor.encryptAndWrite
import net.mamoe.mirai.utils.io.encryptAndWrite
import net.mamoe.mirai.utils.io.writeHex
import net.mamoe.mirai.utils.io.writeIntLVPacket
import net.mamoe.mirai.utils.io.writeQQ

/**
 * 待发送给服务器的数据包. 它代表着一个 [ByteReadPacket].
 * 只有最终的包才会被包装为 [OutgoingPacket].
 */
@UseExperimental(ExperimentalUnsignedTypes::class)
internal class OutgoingPacket constructor(
    name: String?,
    val commandName: String,
    val sequenceId: Int,
    val delegate: ByteReadPacket
) {
    val name: String by lazy {
        name ?: commandName
    }
}

internal val KEY_16_ZEROS = ByteArray(16)
internal val EMPTY_BYTE_ARRAY = ByteArray(0)

/**
 * 最外层的包. 结构适用于登录之后的过程.
 *
 * 在 QQ 中这个被以 JNI 实现:
 * com.tencent.qphone.base.util.CodecWarpper#encodeRequest(int, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, byte[], int, int, java.lang.String, byte, byte, byte, byte[], byte[], boolean)
 *
 * **Packet structure**
 * int      remaining.length + 4
 * int      0x0B
 * byte     0x01
 * int      sequenceId
 * byte     0
 * int      uinAccount.length + 4
 * byte[]   uinAccount
 *
 * byte[]   body encrypted by 16 zero
 */
/*
 * 00 00 02 34  // remaining.length + 4
 * 00 00 00 0B
 * 01
 * 00 01 4E 73 // sequence
 * 00
 * 00 00 00 0E
 * 31 39 39 34 37 30 31 30 32 31
 * 18 5D 8F 17 7D 67 71 61 FE DB 30 A4 4D 16 DD 0E 8D 84 0A F2 44 BE FB BB 11 BB B4 AC 79 50 50 9F 4C 99 CC 77 0B AA B6 E0 06 0C F7 91 79 99 57 31 3D EF 38 92 2C C8 81 33 79 83 FF C6 2F BA 18 2A 33 F8 D9 4E CD 62 07 D8 08 B7 1A 1E C7 EB AC AB B4 1E C9 9D A9 15 9C 29 29 2A 99 F6 BB D0 43 65 D6 5E 9C 93 A8 8D 17 08 5B 6A 29 92 58 6A 75 C9 B5 45 B3 0E A5 D3 52 8F 9A A4 88 36 A0 14 3A 21 F2 46 C3 91 66 A3 73 67 6A 3E F7 9D 8E 44 52 87 7B 8A C7 1B E2 D3 98 62 E8 25 30 2A 43 5C 5A B2 C6 45 F5 39 EC 85 81 BF 7D 22 4C E8 01 87 92 48 38 06 6B A0 83 70 0B 51 ED CF 7A FF E2 F2 06 3E A7 95 4E E5 29 23 32 1C FE 79 C6 08 C5 7A 39 B9 AF CD 4F 80 3E 5D 74 4D 0B E1 10 33 8D F0 54 8E 0E 22 96 B4 06 7F 29 01 1E CA 30 35 FD 8A 2E 51 04 20 79 7B 08 DC DF F6 64 21 6B C5 95 34 B3 40 D2 E8 CE BB DC 69 89 75 62 A6 0B 4A 49 9D 90 BA 68 2B BD 8A 50 2D 68 6B 56 40 0C 39 F2 08 20 1B EB A4 A5 20 1D 1F 7E FA 4B B8 2E 58 79 2A 16 54 26 6C C8 44 6C 4F 64 2D 5C 0C 47 2E 90 13 A9 D7 33 4A 51 17 6E 3F 3E 48 AE 39 D8 45 05 2C 0C 3C 9F 92 39 DB 62 B3 BB 64 EE 7E 91 C5 84 92 10 96 D9 F1 13 02 94 00 EA DA 87 7C 85 7B 68 BA 8D A1 AB F5 CD 9C EB 4C CD A0 38 78 43 80 DD E5 1D 28 25 1F F0 25 EF 0D 95 91 0F 21 5D 41 06 00 03 48 77 E0 98 09 3E 04 5A B0 93 63 3B AE 8E 49 0C C2 12 BA DD C3 5A ED FF 68 98 22 C4 5E F6 1E 85 57 15 E8 7E 26 22 E3 70 C2 57 F4 CE 2F CB C4 DC 39 4A 9C FE DE 27 18 D3 36 66 88 92 D7 69 D0 04 8E 93 9B AD E9 2E 5A 2C 91 CD 28 DF BE 62 CF 2C 72 8E FD A9 1F 0E 8E 00 9E 54 28 50 25 0C E7 DC 98 85 C9 B3 59 A8 97 F5 2E 7F 44 4C 43 3C C4 65 E5 AB DB 5B 3C 50 2D 53 B3 EA 74 3C 39 F4 0A 52 31 34 30 F5 E6 82 CD 36 D9
 */
@UseExperimental(MiraiInternalAPI::class)
internal inline fun PacketFactory<*>.buildOutgingPacket(
    client: QQAndroidClient,
    name: String? = this.commandName,
    commandName: String = this.commandName,
    key: ByteArray,
    body: BytePacketBuilder.(sequenceId: Int) -> Unit
): OutgoingPacket {
    val sequenceId: Int = client.nextSsoSequenceId()

    return OutgoingPacket(name, commandName, sequenceId, buildPacket {
        writeIntLVPacket(lengthOffset = { it + 4 }) {
            writeInt(0x0B)
            writeByte(1)
            writeInt(sequenceId)
            writeByte(0)
            client.uin.toString().let {
                writeInt(it.length + 4)
                writeStringUtf8(it)
            }
            encryptAndWrite(key) {
                body(sequenceId)
            }
        }
    })
}

/**
 * 最外层的包. 结构适用于登录.
 *
 * 在 QQ 中这个被以 JNI 实现:
 * com.tencent.qphone.base.util.CodecWarpper#encodeRequest(int, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, byte[], int, int, java.lang.String, byte, byte, byte, byte[], byte[], boolean)
 *
 * **Packet structure**
 * int      remaining.length + 4
 * int      0x0A
 * byte     0x02
 * int      extra data size + 4
 * byte[]   extra data
 * byte     0
 * int      uinAccount.length + 4
 * byte[]   uinAccount
 *
 * byte[]   body encrypted by 16 zero
 */
@UseExperimental(MiraiInternalAPI::class)
internal inline fun PacketFactory<*>.buildLoginOutgoingPacket(
    client: QQAndroidClient,
    bodyType: Byte,
    extraData: ByteArray = EMPTY_BYTE_ARRAY,
    name: String? = null,
    commandName: String = this.commandName,
    key: ByteArray = KEY_16_ZEROS,
    body: BytePacketBuilder.(sequenceId: Int) -> Unit
): OutgoingPacket {
    val sequenceId: Int = client.nextSsoSequenceId()

    return OutgoingPacket(name, commandName, sequenceId, buildPacket {
        writeIntLVPacket(lengthOffset = { it + 4 }) {
            writeInt(0x00_00_00_0A)
            writeByte(bodyType)
            extraData.let {
                writeInt(it.size + 4)
                writeFully(it)
            }
            writeByte(0x00)

            client.uin.toString().let {
                writeInt(it.length + 4)
                writeStringUtf8(it)
            }

            encryptAndWrite(key) {
                body(sequenceId)
            }
        }
    })
}

private val BRP_STUB = ByteReadPacket(EMPTY_BYTE_ARRAY)

/**
 * The second outermost packet for login
 *
 * int      headRemaining.size+4
 * int      sequenceId
 * int      subAppId
 * int      subAppId
 * hex      "01 00 00 00 00 00 00 00 00 00 01 00" // unknown values
 * int      extraData.size+4
 * byte[]   extraData
 * int      commandName.length+4
 * byte[]   commandName
 * int      4+4
 * int      0x02B05B8B
 * int      imei.length+4
 * byte[]   imei
 * int      0+4
 * int      ksid.length+4
 * byte[]   ksid
 * int      0+4
 *
 * int      bodyRemaining.size+4
 * byte[]   body()
 */
@UseExperimental(MiraiInternalAPI::class)
internal inline fun BytePacketBuilder.writeSsoPacket(
    client: QQAndroidClient,
    subAppId: Long,
    commandName: String,
    extraData: ByteReadPacket = BRP_STUB,
    sequenceId: Int,
    body: BytePacketBuilder.() -> Unit
) {
    writeIntLVPacket(lengthOffset = { it + 4 }) {
        writeInt(sequenceId)
        writeInt(subAppId.toInt())
        writeInt(subAppId.toInt())
        writeHex("01 00 00 00 00 00 00 00 00 00 01 00")
        if (extraData === BRP_STUB) {
            writeInt(0x04)
        } else {
            writeInt((extraData.remaining + 4).toInt())
            writePacket(extraData)
        }
        commandName.let {
            writeInt(it.length + 4)
            writeStringUtf8(it)
        }

        writeInt(4 + 4)
        writeInt(45112203) //  02 B0 5B 8B

        client.device.imei.let {
            writeInt(it.length + 4)
            writeStringUtf8(it)
        }

        writeInt(4)

        client.ksid.let {
            writeShort((it.size + 2).toShort())
            writeFully(it)
        }

        writeInt(4)
    }

    // body
    writeIntLVPacket(lengthOffset = { it + 4 }, builder = body)
}


/**
 * Outermost packet, not for login
 *
 * **Packet structure**
 * int      remaining.length + 4
 * int      0x0B
 * byte     0x01
 * byte     0
 * int      [uinAccount].length + 4
 * byte[]   uinAccount
 *
 * byte[]   body encrypted by [sessionKey]
 */
internal inline fun PacketFactory<*>.buildSessionOutgoingPacket(
    uinAccount: String,
    sessionKey: DecrypterByteArray,
    body: BytePacketBuilder.() -> Unit
): ByteReadPacket = buildPacket {
    writeIntLVPacket(lengthOffset = { it + 4 }) {
        writeInt(0x00_00_00_0B)
        writeByte(0x01)

        writeByte(0)

        writeInt(uinAccount.length + 4)
        writeStringUtf8(uinAccount)

        encryptAndWrite(sessionKey, body)
    }
}

/**
 * Writes a request packet
 * This is the innermost packet structure
 *
 * **Packet Structure**
 * byte     2 // head flag
 * short    27 + 2 + remaining.length
 * ushort   client.protocolVersion // const 8001
 * ushort   0x0001
 * uint     client.uin
 * byte     3 // const
 * ubyte    encryptMethod.value // [EncryptMethod]
 * byte     0 // const
 * int      2 // const
 * int      client.appClientVersion
 * int      0 // const
 * bodyBlock()
 * byte     3 // tail
 */
@UseExperimental(ExperimentalUnsignedTypes::class, MiraiInternalAPI::class)
internal fun BytePacketBuilder.writeOicqRequestPacket(
    client: QQAndroidClient,
    encryptMethod: EncryptMethod,
    commandId: Int,
    bodyBlock: BytePacketBuilder.() -> Unit
) {
    val body = encryptMethod.makeBody(client, bodyBlock)
    // writeIntLVPacket(lengthOffset = { it + 4 }) {
    // Head
    writeByte(0x02) // head
    writeShort((27 + 2 + body.remaining).toShort()) // orthodox algorithm
    writeShort(client.protocolVersion)
    writeShort(commandId.toShort())
    writeShort(1) // const??
    writeQQ(client.uin)
    writeByte(3) // originally const
    writeByte(encryptMethod.id.toByte())
    writeByte(0) // const8_always_0
    writeInt(2) // originally const
    writeInt(client.appClientVersion)
    writeInt(0) // constp_always_0

    // Body
    writePacket(body)

    // Tail
    writeByte(0x03) // tail
    //  }
}
/*
00 00 01 64
00 00 00 0A
02
00 00 00 04
00

00 00 00 0E
31 39 39 34 37 30 31 30 32 31

// encrypted with 16zero
F8 22 FC 39 2E 93 D7 73 A9 75 A2 D4 67 D2 C4 0D F1 02 1F A5 74 8F D8 0E 8E 86 AF 4F 4A A9 C7 74 56 71 B9 03 FC B3 DE A0 F3 14 B7 E9 54 3B 22 F0 24 10 BD 52 88 FC F3 58 66 6C B9 DB 4D 45 2C EF DE 2C C9 E1 1B 27 C7 E2 EF 38 6A 7E 8B 52 3A F4 93 40 E1 A9 ED 10 C3 A3 7E 64 17 02 8F 5C 01 92 72 C7 B8 E0 E1 A5 AF 0B 27 D0 05 C1 33 37 77 37 6D 96 0B B4 1F 41 98 42 35 2C 2A 00 E4 ED E8 C6 42 C4 F4 FD 13 39 D8 E8 19 50 E9 49 06 37 CA CF 42 C3 DD B5 DC B0 E9 87 83 6E 77 AE B6 5C F5 0D 6A 08 67 D0 61 B0 86 39 F7 2E AF E7 B7 C5 F4 42 40 A1 E1 A9 90 55 26 BD C6 03 73 73 BF A2 0A 3F E6 D3 8D B3 69 63 81 83 1E F1 72 5D FA FC 5E 65 B9 C1 FE 77 A8 50 80 F1 A5 DF E0 C4 96 1D 21 CD 5B 70 62 35 51 B5 37 1F 0B 4A 6D 97 92 D0 33 2B 56 11 CB 54 E5 6A A4 B9 97 04 B3 4B 27 A6 61 B7 77 5C C0 D1 6B 98 1C 7A 7B 57 28 3B 80 3B 81 88 69 D2 1C 91 B8 4A DE 0F FD A2 82 F8 3B F6 61 90 84 EF 4A 17 B6 30 1D 09 62 11 C7 BB 00 76 8E 0D 48 1B 11 F4 90 7A 13 0F 09 2B 4E 2F BE FD D9 57 07 18 29 4C 52 23 2E AE

//decrypted:
00 00 00 C1
  00 01 4E 6A
  20 02 ED BD
  20 02 ED BD
  01 00 00 00 00 00 00 00 00 00 01 00
  00 00 00 4C
    B8 12 0D E1 DA 19 AF D3 EB 36 76 BD 42 08 F6 DC A5 35 69 C0 8F F2 75 28 B4 CE 09 C9 B7 86 E3 5A 14 D1 0D CA 5D D4 CB 16 77 8B 32 8D 81 3B 3F D9 52 13 77 03 D3 F7 0E CD 7B 21 95 D2 59 CE 0C 31 D6 F1 38 2A FA 82 AD 60
  00 00 00 14
    47 72 61 79 55 69 6E 50 72 6F 2E 43 68 65 63 6B // serviceCommand
  00 00 00 08
    02 B0 5B 8B
  00 00 00 13
    38 35 38 34 31 34 33 36 39 32 31 31 39 39 33
  00 00 00 04
  00 22
  7C 34 35 34 30 30 31 32 32 38 34 33 37 35 39 30 7C 41 38 2E 32 2E 30 2E 32 37 66 36 65 61 39 36 00 00 00 04

00 00 00 7A // UniPacket
  10
  03 2C
  3C 42 00 01 4E 69 56 22 4B 51 51 2E 43 6F 6E 66 69 67 53 65 72 76 69 63 65 2E 43 6F 6E 66 69 67 53 65 72 76 61 6E 74 4F 62 6A 66 09 43 6C 69 65 6E 74 52 65 71 7D 00 00 35 08 00 01 06 03 72 65 71 1D 00 00 29 0A 12 20 02 ED BD 26 0A 31 39 39 34 37 30 31 30 32 31 36 00 46 12 31 30 31 31 30 33 30 38 33 38 34 36 30 36 32 30 34 32 0B 8C 98 0C A8 0C

 */
/*
00 00 00 FC
00 00 00 0B
01 // packet type?
00 01 50 DE
00

00 00 00 0E
31 39 39 34 37 30 31 30 32 31

4E 32 1B 0F 07 DC 39 FE 14 78 ED 32 60 C4 07 31 9D CD 1A E0 C4 F6 21 6B EA 52 A4 F4 C1 D2 AF FB 17 5A C4 15 BC 35 BC 45 58 B6 11 19 DA AF 12 91 B5 A0 5D E4 FD 5A 49 1A 55 71 45 89 6F 3A 09 E6 32 F4 96 4A BB B2 EE 35 B9 39 63 5B FF E3 F0 94 69 67 99 64 A2 03 23 D0 F7 74 81 D1 20 F8 20 E6 F3 5B E6 C2 A2 25 6F 90 C5 DA CB D2 08 9D 5D 83 47 F3 27 3F 41 19 E5 9A C0 F2 05 70 B2 C5 DC F9 F1 6D 2A E9 92 84 9C 8D 98 04 E8 A1 3B 40 F2 71 60 9F 2C D8 6A CD 6B F5 2B 12 68 C7 9C 6B 0E D2 F7 16 40 47 72 3D 6A AF 36 2E 43 0C 96 28 C7 A6 B1 04 3B 29 F6 8B A4 E0 47 1A 3D 51 32 C7 AF A5 7E FD F7 50 FC 81 3D 13 45 60 6B 8D F4 A6 9B E7 46 D4 1E 9B 2C 00 D0 24 2F 0E 44 29 43 A8 F6 25

 */
/*
00 00 01 04
00 00 00 0B
01
00 01 50 CE
00

00 00 00 0E 31 39 39 34 37 30 31 30 32 31 D2 D5 37 8A 3C 47 B1 84 E2 94 B2 AF BF 14 70 4D 73 17 BB 38 BE 82 73 DF A2 87 E0 0A 7A BA 8A 81 71 77 1D E1 71 7F B7 C1 66 1D 8C 3D 41 4F 51 09 6A B7 B7 7B 88 28 A6 5A AB 7E 40 25 9B C8 35 9C C6 E2 3A 5F 94 1D 70 0F D7 89 4D 41 6B 7A 29 A2 70 77 3D F8 1D 32 65 D7 D8 D1 6D 13 42 9C 0C 72 DB 48 95 4B 66 EF B9 E6 E4 C1 3B 2C 36 B0 D7 3F E2 85 C8 2A 8C 65 0F 0B 1C F1 A7 C7 E1 1F 0C 32 F5 08 14 AA 5A 43 CD 8E A8 82 14 24 97 63 F0 53 79 4E 33 8D 5F 1C F8 1C 89 3B 39 44 CC A7 63 5F FC BF 87 42 89 2D A5 F4 BC B2 69 49 54 DD AE E6 3F A2 A2 98 DC 3B D4 A2 27 10 F2 06 42 93 C5 30 4A D4 FA F5 BA A5 B2 4B 56 45 59 94 CA 4C 4B 17 55 C7 23 AF F0 8B E5 DC 3A 1B B6 A7 2E 10 BB 9A E7 70 54 BA F5 4B 70 91

 */