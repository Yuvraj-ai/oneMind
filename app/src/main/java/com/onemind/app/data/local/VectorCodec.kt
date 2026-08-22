package com.onemind.app.data.local

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Packs embedding vectors to and from the BLOB column they are stored in.
 *
 * Little-endian throughout, fixed explicitly rather than left to the platform
 * default, so a database file stays readable if it is ever moved between
 * architectures.
 */
object VectorCodec {

    private const val BYTES_PER_FLOAT = 4

    fun encode(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer
            .allocate(vector.size * BYTES_PER_FLOAT)
            .order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach(buffer::putFloat)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): FloatArray {
        require(bytes.size % BYTES_PER_FLOAT == 0) {
            "Vector blob of ${bytes.size} bytes is not a whole number of floats"
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / BYTES_PER_FLOAT) { buffer.getFloat() }
    }
}
