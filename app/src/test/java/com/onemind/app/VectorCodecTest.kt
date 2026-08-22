package com.onemind.app

import com.onemind.app.data.local.VectorCodec
import org.junit.Assert.*
import org.junit.Test

class VectorCodecTest {

    @Test
    fun `round trips a vector unchanged`() {
        val original = FloatArray(384) { it * 0.01f }
        val decoded = VectorCodec.decode(VectorCodec.encode(original))
        assertArrayEquals(original, decoded, 0.0f)
    }

    @Test
    fun `packs four bytes per float`() {
        assertEquals(384 * 4, VectorCodec.encode(FloatArray(384)).size)
    }

    @Test
    fun `round trips negative and fractional values exactly`() {
        val original = floatArrayOf(-1.0f, -0.5f, 0.0f, 0.5f, 1.0f, -0.123456f)
        val decoded = VectorCodec.decode(VectorCodec.encode(original))
        assertArrayEquals(original, decoded, 0.0f)
    }

    @Test
    fun `handles an empty vector`() {
        assertEquals(0, VectorCodec.encode(FloatArray(0)).size)
        assertEquals(0, VectorCodec.decode(ByteArray(0)).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a blob that is not a whole number of floats`() {
        VectorCodec.decode(ByteArray(7))
    }
}
