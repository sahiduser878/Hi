package com.example.util

/**
 * Pure Kotlin QR Code Matrix Generator (Model 2, Version 1 to 4).
 * Generates an N x N boolean matrix representing QR code dark modules.
 */
object QrEncoder {

    data class QrMatrix(val width: Int, val height: Int, val modules: Array<BooleanArray>) {
        operator fun get(x: Int, y: Int): Boolean = modules[y][x]

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is QrMatrix) return false
            return width == other.width && height == other.height && modules.contentDeepEquals(other.modules)
        }

        override fun hashCode(): Int {
            return modules.contentDeepHashCode()
        }
    }

    /**
     * Encodes a string into a QR code boolean matrix.
     */
    fun encode(text: String): QrMatrix {
        val version = getMinVersion(text.length)
        val size = 21 + (version - 1) * 4
        val modules = Array(size) { BooleanArray(size) { false } }
        val reserved = Array(size) { BooleanArray(size) { false } }

        // 1. Finder patterns at (0,0), (size-7, 0), (0, size-7)
        addFinderPattern(modules, reserved, 0, 0)
        addFinderPattern(modules, reserved, size - 7, 0)
        addFinderPattern(modules, reserved, 0, size - 7)

        // 2. Separators around finder patterns
        addSeparators(reserved, size)

        // 3. Timing patterns
        for (i in 8 until size - 8) {
            val bit = (i % 2 == 0)
            if (!reserved[6][i]) {
                modules[6][i] = bit
                reserved[6][i] = true
            }
            if (!reserved[i][6]) {
                modules[i][6] = bit
                reserved[i][6] = true
            }
        }

        // 4. Alignment patterns for version >= 2
        if (version >= 2) {
            val alignPositions = getAlignmentPatternPositions(version)
            for (r in alignPositions) {
                for (c in alignPositions) {
                    if (!reserved[r][c]) {
                        addAlignmentPattern(modules, reserved, c - 2, r - 2)
                    }
                }
            }
        }

        // 5. Reserve format info area
        reserveFormatInfo(reserved, size)

        // 6. Data payload generation
        val dataBits = generateDataBits(text, version)
        val totalCapacityBits = getCapacityBits(version)

        val paddedBits = ArrayList<Boolean>(dataBits)
        // Add terminator (up to 4 zeroes)
        var count = 0
        while (paddedBits.size < totalCapacityBits && count < 4) {
            paddedBits.add(false)
            count++
        }
        // Round up to multiple of 8
        while (paddedBits.size % 8 != 0 && paddedBits.size < totalCapacityBits) {
            paddedBits.add(false)
        }
        // Pad bytes (0xEC, 0x11)
        var padToggle = true
        while (paddedBits.size < totalCapacityBits) {
            val padByte = if (padToggle) 0xEC else 0x11
            padToggle = !padToggle
            for (b in 7 downTo 0) {
                if (paddedBits.size < totalCapacityBits) {
                    paddedBits.add(((padByte shr b) and 1) == 1)
                }
            }
        }

        // 7. Place data bits using zigzag pattern
        var bitIndex = 0
        var right = size - 1
        var upward = true
        while (right > 0) {
            if (right == 6) right-- // Skip timing col
            val rows = if (upward) (size - 1 downTo 0) else (0 until size)
            for (y in rows) {
                for (col in 0..1) {
                    val x = right - col
                    if (!reserved[y][x]) {
                        val bit = if (bitIndex < paddedBits.size) paddedBits[bitIndex++] else false
                        // Mask pattern 0: (x + y) % 2 == 0
                        val mask = ((x + y) % 2 == 0)
                        modules[y][x] = bit xor mask
                    }
                }
            }
            upward = !upward
            right -= 2
        }

        // 8. Place format info with Mask 0 & EC Level L (01 000 -> 0x08 -> BCH -> 0x77C4)
        applyFormatInfo(modules, size)

        return QrMatrix(size, size, modules)
    }

    private fun getMinVersion(length: Int): Int {
        return when {
            length <= 14 -> 1
            length <= 26 -> 2
            length <= 42 -> 3
            else -> 4
        }
    }

    private fun getCapacityBits(version: Int): Int {
        // Data bits capacity for EC Level L
        return when (version) {
            1 -> 19 * 8
            2 -> 34 * 8
            3 -> 55 * 8
            else -> 80 * 8
        }
    }

    private fun generateDataBits(text: String, version: Int): List<Boolean> {
        val bits = ArrayList<Boolean>()
        // Mode indicator for 8-bit byte mode: 0100
        bits.add(false); bits.add(true); bits.add(false); bits.add(false)

        // Character count indicator: 8 bits for versions 1-9 in byte mode
        val length = text.length
        for (i in 7 downTo 0) {
            bits.add(((length shr i) and 1) == 1)
        }

        // Character data
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            for (i in 7 downTo 0) {
                bits.add(((v shr i) and 1) == 1)
            }
        }
        return bits
    }

    private fun addFinderPattern(modules: Array<BooleanArray>, reserved: Array<BooleanArray>, startX: Int, startY: Int) {
        for (y in 0..6) {
            for (x in 0..6) {
                val isBlack = (y == 0 || y == 6 || x == 0 || x == 6 || (y in 2..4 && x in 2..4))
                modules[startY + y][startX + x] = isBlack
                reserved[startY + y][startX + x] = true
            }
        }
    }

    private fun addSeparators(reserved: Array<BooleanArray>, size: Int) {
        for (i in 0..7) {
            if (i < size) {
                // Top-Left
                reserved[7][i] = true
                reserved[i][7] = true
                // Top-Right
                if (size - 8 + i < size) {
                    reserved[7][size - 8 + i] = true
                    reserved[i][size - 8] = true
                }
                // Bottom-Left
                if (size - 8 + i < size) {
                    reserved[size - 8][i] = true
                    reserved[size - 8 + i][7] = true
                }
            }
        }
    }

    private fun getAlignmentPatternPositions(version: Int): IntArray {
        return when (version) {
            2 -> intArrayOf(6, 18)
            3 -> intArrayOf(6, 22)
            4 -> intArrayOf(6, 26)
            else -> intArrayOf(6, 18)
        }
    }

    private fun addAlignmentPattern(modules: Array<BooleanArray>, reserved: Array<BooleanArray>, startX: Int, startY: Int) {
        for (y in 0..4) {
            for (x in 0..4) {
                val isBlack = (y == 0 || y == 4 || x == 0 || x == 4 || (x == 2 && y == 2))
                modules[startY + y][startX + x] = isBlack
                reserved[startY + y][startX + x] = true
            }
        }
    }

    private fun reserveFormatInfo(reserved: Array<BooleanArray>, size: Int) {
        for (i in 0..8) {
            reserved[8][i] = true
            reserved[i][8] = true
        }
        for (i in 0..7) {
            reserved[8][size - 1 - i] = true
            reserved[size - 1 - i][8] = true
        }
    }

    private fun applyFormatInfo(modules: Array<BooleanArray>, size: Int) {
        // Format info for EC Level L (01), Mask 0 (000) -> 15 bits: 111011111000100
        val formatBits = booleanArrayOf(
            true, true, true, false, true, true, true, true,
            true, false, false, false, true, false, false
        )

        // Top-left
        var bitIdx = 0
        for (i in 0..5) modules[8][i] = formatBits[bitIdx++]
        modules[8][7] = formatBits[bitIdx++]
        modules[8][8] = formatBits[bitIdx++]
        modules[7][8] = formatBits[bitIdx++]
        for (i in 5 downTo 0) modules[i][8] = formatBits[bitIdx++]

        // Second copy
        bitIdx = 0
        for (i in 0..6) modules[size - 1 - i][8] = formatBits[bitIdx++]
        for (i in 7 downTo 0) modules[8][size - 8 + i] = formatBits[bitIdx++]
    }
}
