package plus.kite.explore.core

import kotlin.math.abs
import kotlin.math.max

/**
 * A QR code for share links: byte mode, the smallest version that fits, and the mask with
 * the lowest penalty. Follows ISO/IEC 18004; Android has no built-in encoder.
 */
class QrCode private constructor(val size: Int, private val modules: Array<BooleanArray>) {
    fun isDark(x: Int, y: Int): Boolean = modules[y][x]

    enum class Correction(val formatBits: Int) { L(1), M(0), Q(3), H(2) }

    companion object {
        /** Share links are short, so medium error correction still leaves a sparse code. */
        fun encode(text: String, correction: Correction = Correction.M): QrCode {
            val data = text.encodeToByteArray()
            val level = correction.ordinal
            var version = 1
            while (true) {
                val countBits = if (version <= 9) 8 else 16
                val capacityBits = dataCodewords(version, level) * 8
                if (4 + countBits + data.size * 8 <= capacityBits) break
                require(version < 40) { "Text too long for a QR code" }
                version++
            }
            val bits = BitBuffer()
            bits.append(0b0100, 4)
            bits.append(data.size, if (version <= 9) 8 else 16)
            for (byte in data) bits.append(byte.toInt() and 0xFF, 8)
            val capacityBits = dataCodewords(version, level) * 8
            bits.append(0, minOf(4, capacityBits - bits.size))
            bits.append(0, (8 - bits.size % 8) % 8)
            var pad = 0xEC
            while (bits.size < capacityBits) {
                bits.append(pad, 8)
                pad = pad xor 0xEC xor 0x11
            }
            val codewords = ByteArray(bits.size / 8)
            for (i in 0 until bits.size) {
                if (bits[i]) codewords[i ushr 3] = (codewords[i ushr 3].toInt() or (1 shl (7 - (i and 7)))).toByte()
            }
            return Builder(version, correction).build(interleave(codewords, version, level))
        }

        private val eccPerBlock = arrayOf(
            intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
            intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),
            intArrayOf(-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
            intArrayOf(-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
        )

        private val blockCount = arrayOf(
            intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),
            intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),
            intArrayOf(-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68),
            intArrayOf(-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81),
        )

        private fun rawDataModules(version: Int): Int {
            var result = (16 * version + 128) * version + 64
            if (version >= 2) {
                val align = version / 7 + 2
                result -= (25 * align - 10) * align - 55
                if (version >= 7) result -= 36
            }
            return result
        }

        private fun dataCodewords(version: Int, level: Int): Int =
            rawDataModules(version) / 8 - eccPerBlock[level][version] * blockCount[level][version]

        /** Splits the data into blocks, adds each block's error correction and interleaves them. */
        private fun interleave(data: ByteArray, version: Int, level: Int): ByteArray {
            val blocks = blockCount[level][version]
            val eccLength = eccPerBlock[level][version]
            val raw = rawDataModules(version) / 8
            val shortBlocks = blocks - raw % blocks
            val shortLength = raw / blocks
            val divisor = divisor(eccLength)
            val all = ArrayList<ByteArray>(blocks)
            var offset = 0
            for (i in 0 until blocks) {
                val length = shortLength - eccLength + if (i < shortBlocks) 0 else 1
                val chunk = data.copyOfRange(offset, offset + length)
                offset += length
                val ecc = remainder(chunk, divisor)
                val padded = if (i < shortBlocks) chunk + byteArrayOf(0) else chunk
                all += padded + ecc
            }
            val result = ByteArray(raw)
            var k = 0
            for (i in all[0].indices) {
                for (j in all.indices) {
                    if (i != shortLength - eccLength || j >= shortBlocks) result[k++] = all[j][i]
                }
            }
            return result
        }

        private fun divisor(degree: Int): IntArray {
            val result = IntArray(degree)
            result[degree - 1] = 1
            var root = 1
            for (i in 0 until degree) {
                for (j in result.indices) {
                    result[j] = multiply(result[j], root)
                    if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
                }
                root = multiply(root, 0x02)
            }
            return result
        }

        private fun remainder(data: ByteArray, divisor: IntArray): ByteArray {
            val result = IntArray(divisor.size)
            for (byte in data) {
                val factor = (byte.toInt() and 0xFF) xor result[0]
                System.arraycopy(result, 1, result, 0, result.size - 1)
                result[result.size - 1] = 0
                for (i in result.indices) result[i] = result[i] xor multiply(divisor[i], factor)
            }
            return ByteArray(result.size) { result[it].toByte() }
        }

        private fun multiply(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }
    }

    private class BitBuffer {
        private val bits = ArrayList<Boolean>()
        val size: Int get() = bits.size
        operator fun get(index: Int): Boolean = bits[index]
        fun append(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits += (value ushr i) and 1 != 0
        }
    }

    private class Builder(private val version: Int, private val correction: Correction) {
        private val size = version * 4 + 17
        private val modules = Array(size) { BooleanArray(size) }
        private val function = Array(size) { BooleanArray(size) }

        fun build(codewords: ByteArray): QrCode {
            drawFunctionPatterns()
            drawCodewords(codewords)
            var best = 0
            var bestPenalty = Int.MAX_VALUE
            for (mask in 0 until 8) {
                applyMask(mask)
                drawFormatBits(mask)
                val penalty = penalty()
                if (penalty < bestPenalty) {
                    best = mask
                    bestPenalty = penalty
                }
                applyMask(mask)
            }
            applyMask(best)
            drawFormatBits(best)
            return QrCode(size, modules)
        }

        private fun set(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            function[y][x] = true
        }

        private fun drawFunctionPatterns() {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            finder(3, 3)
            finder(size - 4, 3)
            finder(3, size - 4)
            val positions = alignmentPositions()
            val count = positions.size
            for (i in 0 until count) {
                for (j in 0 until count) {
                    val corner = (i == 0 && j == 0) || (i == 0 && j == count - 1) || (i == count - 1 && j == 0)
                    if (!corner) alignment(positions[i], positions[j])
                }
            }
            drawFormatBits(0)
            drawVersion()
        }

        private fun finder(x: Int, y: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val distance = max(abs(dx), abs(dy))
                    val xx = x + dx
                    val yy = y + dy
                    if (xx in 0 until size && yy in 0 until size) set(xx, yy, distance != 2 && distance != 4)
                }
            }
        }

        private fun alignment(x: Int, y: Int) {
            for (dy in -2..2) {
                for (dx in -2..2) set(x + dx, y + dy, max(abs(dx), abs(dy)) != 1)
            }
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val count = version / 7 + 2
            val step = (version * 8 + count * 3 + 5) / (count * 4 - 4) * 2
            val result = IntArray(count)
            result[0] = 6
            var position = size - 7
            for (i in count - 1 downTo 1) {
                result[i] = position
                position -= step
            }
            return result
        }

        private fun drawFormatBits(mask: Int) {
            val data = correction.formatBits shl 3 or mask
            var remainder = data
            repeat(10) { remainder = (remainder shl 1) xor ((remainder ushr 9) * 0x537) }
            val bits = (data shl 10 or remainder) xor 0x5412
            fun bit(i: Int) = (bits ushr i) and 1 != 0
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6))
            set(8, 8, bit(7))
            set(7, 8, bit(8))
            for (i in 9 until 15) set(14 - i, 8, bit(i))
            for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true)
        }

        private fun drawVersion() {
            if (version < 7) return
            var remainder = version
            repeat(12) { remainder = (remainder shl 1) xor ((remainder ushr 11) * 0x1F25) }
            val bits = version shl 12 or remainder
            for (i in 0 until 18) {
                val dark = (bits ushr i) and 1 != 0
                val a = size - 11 + i % 3
                val b = i / 3
                set(a, b, dark)
                set(b, a, dark)
            }
        }

        private fun drawCodewords(data: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vertical in 0 until size) {
                    for (j in 0 until 2) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) size - 1 - vertical else vertical
                        if (!function[y][x] && i < data.size * 8) {
                            modules[y][x] = ((data[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) != 0
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        private fun applyMask(mask: Int) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val invert = when (mask) {
                        0 -> (x + y) % 2 == 0
                        1 -> y % 2 == 0
                        2 -> x % 3 == 0
                        3 -> (x + y) % 3 == 0
                        4 -> (x / 3 + y / 2) % 2 == 0
                        5 -> x * y % 2 + x * y % 3 == 0
                        6 -> (x * y % 2 + x * y % 3) % 2 == 0
                        else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                    }
                    if (invert && !function[y][x]) modules[y][x] = !modules[y][x]
                }
            }
        }

        /** Runs, 2x2 blocks, finder-like patterns and dark balance, as the standard scores them. */
        private fun penalty(): Int {
            var result = 0
            for (line in 0 until size) {
                result += runPenalty { modules[line][it] }
                result += runPenalty { modules[it][line] }
                result += finderLikePenalty { modules[line][it] }
                result += finderLikePenalty { modules[it][line] }
            }
            for (y in 0 until size - 1) {
                for (x in 0 until size - 1) {
                    val color = modules[y][x]
                    if (color == modules[y][x + 1] && color == modules[y + 1][x] && color == modules[y + 1][x + 1]) result += 3
                }
            }
            var dark = 0
            for (row in modules) for (module in row) if (module) dark++
            val total = size * size
            val k = ((abs(dark * 20L - total * 10L) + total - 1) / total).toInt() - 1
            result += k * 10
            return result
        }

        private inline fun runPenalty(module: (Int) -> Boolean): Int {
            var result = 0
            var run = 1
            for (i in 1 until size) {
                if (module(i) == module(i - 1)) {
                    run++
                } else {
                    if (run >= 5) result += 3 + run - 5
                    run = 1
                }
            }
            if (run >= 5) result += 3 + run - 5
            return result
        }

        private inline fun finderLikePenalty(module: (Int) -> Boolean): Int {
            var result = 0
            for (i in 0..size - 11) {
                var forward = true
                var backward = true
                for (k in 0 until 11) {
                    val dark = module(i + k)
                    if (dark != patternA[k]) forward = false
                    if (dark != patternB[k]) backward = false
                }
                if (forward) result += 40
                if (backward) result += 40
            }
            return result
        }

        private val patternA = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
        private val patternB = booleanArrayOf(false, false, false, false, true, false, true, true, true, false, true)
    }
}
