/*
 * Copyright 2017-2019 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Some code changed by Mamoe is annotated around "MIRAI MODIFY START" and "MIRAI MODIFY END"
 */

@file:Suppress("DEPRECATION_ERROR")

package net.mamoe.mirai.internal.utils.io.serialization

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.MapEntrySerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.internal.MapLikeSerializer
import kotlinx.serialization.internal.TaggedEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoIntegerType
import kotlinx.serialization.protobuf.ProtoType
import net.mamoe.mirai.internal.utils.io.serialization.ProtoBufWithNullableSupport.Varint.encodeVarint
import net.mamoe.mirai.internal.utils.io.serialization.tars.TarsId
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal typealias ProtoDesc = Pair<Int, ProtoIntegerType>

internal fun getSerialId(desc: SerialDescriptor, index: Int): Int? = desc.findAnnotation<TarsId>(index)?.id

internal fun extractParameters(desc: SerialDescriptor, index: Int, zeroBasedDefault: Boolean = false): ProtoDesc {
    val idx = getSerialId(desc, index) ?: (if (zeroBasedDefault) index else index + 1)
    val format = desc.findAnnotation<ProtoType>(index)?.type
        ?: ProtoIntegerType.DEFAULT
    return idx to format
}


/**
 * 带有 null (optional) support 的 Protocol buffers 序列化器.
 * 所有的为 null 的属性都将不会被序列化. 以此实现可选属性.
 *
 * 代码复制自 kotlinx.serialization. 修改部分已进行标注 (详见 "MIRAI MODIFY START")
 */
@OptIn(InternalSerializationApi::class)
internal class ProtoBufWithNullableSupport(override val serializersModule: SerializersModule = EmptySerializersModule) :
    SerialFormat, BinaryFormat {

    internal open inner class ProtobufWriter(private val encoder: ProtobufEncoder) : TaggedEncoder<ProtoDesc>() {
        override val serializersModule
            get() = this@ProtoBufWithNullableSupport.serializersModule

        @Suppress("OverridingDeprecatedMember")
        override fun beginStructure(
            descriptor: SerialDescriptor,
            vararg typeSerializers: KSerializer<*>
        ): CompositeEncoder = when (descriptor.kind) {
            StructureKind.LIST -> RepeatedWriter(encoder, currentTag)
            StructureKind.CLASS, StructureKind.OBJECT, is PolymorphicKind -> ObjectWriter(currentTagOrNull, encoder)
            StructureKind.MAP -> MapRepeatedWriter(currentTagOrNull, encoder)
            else -> throw SerializationException("Primitives are not supported at top-level")
        }

        override fun encodeTaggedInt(tag: ProtoDesc, value: Int) = encoder.writeInt(value, tag.first, tag.second)
        override fun encodeTaggedByte(tag: ProtoDesc, value: Byte) = encoder.writeInt(value.toInt(), tag.first, tag.second)
        override fun encodeTaggedShort(tag: ProtoDesc, value: Short) = encoder.writeInt(value.toInt(), tag.first, tag.second)
        override fun encodeTaggedLong(tag: ProtoDesc, value: Long) = encoder.writeLong(value, tag.first, tag.second)
        override fun encodeTaggedFloat(tag: ProtoDesc, value: Float) = encoder.writeFloat(value, tag.first)
        override fun encodeTaggedDouble(tag: ProtoDesc, value: Double) = encoder.writeDouble(value, tag.first)
        override fun encodeTaggedBoolean(tag: ProtoDesc, value: Boolean) = encoder.writeInt(if (value) 1 else 0, tag.first, ProtoIntegerType.DEFAULT)
        override fun encodeTaggedChar(tag: ProtoDesc, value: Char) = encoder.writeInt(value.toInt(), tag.first, tag.second)
        override fun encodeTaggedString(tag: ProtoDesc, value: String) = encoder.writeString(value, tag.first)
        override fun encodeTaggedEnum(
            tag: ProtoDesc,
            enumDescriptor: SerialDescriptor,
            ordinal: Int
        ) = encoder.writeInt(
            extractParameters(enumDescriptor, ordinal, zeroBasedDefault = true).first,
            tag.first,
            ProtoIntegerType.DEFAULT
        )

        override fun SerialDescriptor.getTag(index: Int) = this.getProtoDesc(index)

        // MIRAI MODIFY START
        override fun encodeTaggedNull(tag: ProtoDesc) {

        }

        override fun <T : Any> encodeNullableSerializableValue(serializer: SerializationStrategy<T>, value: T?) {
            if (value == null) {
                encodeTaggedNull(popTag())
            } else encodeSerializableValue(serializer, value)
        }
        // MIRAI MODIFY END

        @Suppress("UNCHECKED_CAST", "NAME_SHADOWING")
        override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) = when {
            // encode maps as collection of map entries, not merged collection of key-values
            serializer.descriptor.kind == StructureKind.MAP -> {
                val serializer = (serializer as MapLikeSerializer<Any?, Any?, T, *>)
                val mapEntrySerial = MapEntrySerializer(serializer.keySerializer, serializer.valueSerializer)
                SetSerializer(mapEntrySerial).serialize(this, (value as Map<*, *>).entries)
            }
            serializer.descriptor == ByteArraySerializer().descriptor -> encoder.writeBytes(
                value as ByteArray,
                popTag().first
            )
            else -> serializer.serialize(this, value)
        }
    }

    internal open inner class ObjectWriter(
        val parentTag: ProtoDesc?, private val parentEncoder: ProtobufEncoder,
        private val stream: ByteArrayOutputStream = ByteArrayOutputStream()
    ) : ProtobufWriter(
        ProtobufEncoder(
            stream
        )
    ) {
        override fun endEncode(descriptor: SerialDescriptor) {
            if (parentTag != null) {
                parentEncoder.writeBytes(stream.toByteArray(), parentTag.first)
            } else {
                parentEncoder.out.write(stream.toByteArray())
            }
        }
    }

    internal inner class MapRepeatedWriter(parentTag: ProtoDesc?, parentEncoder: ProtobufEncoder) : ObjectWriter(parentTag, parentEncoder) {
        override fun SerialDescriptor.getTag(index: Int): ProtoDesc =
            if (index % 2 == 0) 1 to (parentTag?.second ?: ProtoIntegerType.DEFAULT)
            else 2 to (parentTag?.second ?: ProtoIntegerType.DEFAULT)
    }

    internal inner class RepeatedWriter(encoder: ProtobufEncoder, private val curTag: ProtoDesc) :
        ProtobufWriter(encoder) {
        override fun SerialDescriptor.getTag(index: Int) = curTag
    }

    internal class ProtobufEncoder(val out: ByteArrayOutputStream) {

        fun writeBytes(bytes: ByteArray, tag: Int) {
            val header = encode32((tag shl 3) or SIZE_DELIMITED)
            val len = encode32(bytes.size)
            out.write(header)
            out.write(len)
            out.write(bytes)
        }

        fun writeInt(value: Int, tag: Int, format: ProtoIntegerType) {
            val wireType = if (format == ProtoIntegerType.FIXED) i32 else VARINT
            val header = encode32((tag shl 3) or wireType)
            val content = encode32(value, format)
            out.write(header)
            out.write(content)
        }

        fun writeLong(value: Long, tag: Int, format: ProtoIntegerType) {
            val wireType = if (format == ProtoIntegerType.FIXED) i64 else VARINT
            val header = encode32((tag shl 3) or wireType)
            val content = encode64(value, format)
            out.write(header)
            out.write(content)
        }

        @OptIn(ExperimentalStdlibApi::class)
        fun writeString(value: String, tag: Int) {
            val bytes = value.encodeToByteArray()
            writeBytes(bytes, tag)
        }

        fun writeDouble(value: Double, tag: Int) {
            val header = encode32((tag shl 3) or i64)
            val content = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(value).array()
            out.write(header)
            out.write(content)
        }

        fun writeFloat(value: Float, tag: Int) {
            val header = encode32((tag shl 3) or i32)
            val content = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array()
            out.write(header)
            out.write(content)
        }

        private fun encode32(number: Int, format: ProtoIntegerType = ProtoIntegerType.DEFAULT): ByteArray =
            when (format) {
                ProtoIntegerType.FIXED -> ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(number).array()
                ProtoIntegerType.DEFAULT -> encodeVarint(number.toLong())
                ProtoIntegerType.SIGNED -> encodeVarint(((number shl 1) xor (number shr 31)))
            }


        private fun encode64(number: Long, format: ProtoIntegerType = ProtoIntegerType.DEFAULT): ByteArray =
            when (format) {
                ProtoIntegerType.FIXED -> ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(number).array()
                ProtoIntegerType.DEFAULT -> encodeVarint(number)
                ProtoIntegerType.SIGNED -> encodeVarint((number shl 1) xor (number shr 63))
            }
    }

    /**
     *  Source for all varint operations:
     *  https://github.com/addthis/stream-lib/blob/master/src/main/java/com/clearspring/analytics/util/Varint.java
     */
    @Suppress("unused")
    internal object Varint {
        internal fun encodeVarint(inp: Int): ByteArray {
            var value = inp
            val byteArrayList = ByteArray(10)
            var i = 0
            while (value and 0xFFFFFF80.toInt() != 0) {
                byteArrayList[i++] = ((value and 0x7F) or 0x80).toByte()
                value = value ushr 7
            }
            byteArrayList[i] = (value and 0x7F).toByte()
            val out = ByteArray(i + 1)
            while (i >= 0) {
                out[i] = byteArrayList[i]
                i--
            }
            return out
        }

        internal fun encodeVarint(inp: Long): ByteArray {
            var value = inp
            val byteArrayList = ByteArray(10)
            var i = 0
            while (value and 0x7FL.inv() != 0L) {
                byteArrayList[i++] = ((value and 0x7F) or 0x80).toByte()
                value = value ushr 7
            }
            byteArrayList[i] = (value and 0x7F).toByte()
            val out = ByteArray(i + 1)
            while (i >= 0) {
                out[i] = byteArrayList[i]
                i--
            }
            return out
        }
    }

    companion object : BinaryFormat {
        override val serializersModule: SerializersModule
            get() = plain.serializersModule

        private fun SerialDescriptor.getProtoDesc(index: Int): ProtoDesc {
            return extractParameters(this, index)
        }

        internal const val VARINT = 0
        internal const val i64 = 1
        internal const val SIZE_DELIMITED = 2
        internal const val i32 = 5

        private val plain = ProtoBufWithNullableSupport()

        override fun <T> encodeToByteArray(serializer: SerializationStrategy<T>, value: T): ByteArray {
            return plain.encodeToByteArray(serializer, value)
        }

        override fun <T> decodeFromByteArray(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T {
            return plain.decodeFromByteArray(deserializer, bytes)
        }
    }

    override fun <T> encodeToByteArray(serializer: SerializationStrategy<T>, value: T): ByteArray {
        val encoder = ByteArrayOutputStream()
        val dumper = ProtobufWriter(ProtobufEncoder(encoder))
        dumper.encodeSerializableValue(serializer, value)
        return encoder.toByteArray()
    }

    override fun <T> decodeFromByteArray(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T {
        return ProtoBuf.decodeFromByteArray(deserializer, bytes)
    }

}

