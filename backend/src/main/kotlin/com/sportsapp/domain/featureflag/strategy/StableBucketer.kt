package com.sportsapp.domain.featureflag.strategy

/**
 * 안정 키(stable key) 버케팅 — `murmur3_32("{flagKey}:{userId}")` → 0..99 정규화.
 *
 * flagKey를 salt로 사용해 플래그마다 노출 집합이 독립적으로 분포하게 한다 (Unleash stickiness 참조).
 * 동일 flagKey·userId 조합은 항상 동일한 버킷을 반환한다 — 퍼센티지 롤아웃·EXPERIMENT variant 배정이 공유한다.
 */
object StableBucketer {

    private const val SEED = 0
    private const val C1 = -0x3361d2af // 0xcc9e2d51
    private const val C2 = 0x1b873593
    private const val ROTATE_K = 15
    private const val ROTATE_HASH = 13
    private const val MULTIPLIER = 5
    private const val ADDEND = -0x19ab949c // 0xe6546b64
    private const val BUCKET_RANGE = 100

    fun bucket(flagKey: String, userId: Long): Int {
        val hash = murmurHash3x86("$flagKey:$userId")
        return Math.floorMod(hash, BUCKET_RANGE)
    }

    private fun murmurHash3x86(input: String): Int {
        val data = input.toByteArray(Charsets.UTF_8)
        val blockCount = data.size / 4

        var hash = SEED
        for (blockIndex in 0 until blockCount) {
            hash = mixBlock(hash, readBlock(data, blockIndex * 4))
        }
        hash = hash xor mixTail(readTail(data, blockCount * 4))
        hash = hash xor data.size
        return fmix32(hash)
    }

    private fun readBlock(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xff) or
            ((data[offset + 1].toInt() and 0xff) shl 8) or
            ((data[offset + 2].toInt() and 0xff) shl 16) or
            ((data[offset + 3].toInt() and 0xff) shl 24)

    private fun readTail(data: ByteArray, tailStart: Int): Int {
        var tail = 0
        val remaining = data.size - tailStart
        if (remaining >= 3) tail = tail xor ((data[tailStart + 2].toInt() and 0xff) shl 16)
        if (remaining >= 2) tail = tail xor ((data[tailStart + 1].toInt() and 0xff) shl 8)
        if (remaining >= 1) tail = tail xor (data[tailStart].toInt() and 0xff)
        return tail
    }

    private fun mixBlock(hash: Int, block: Int): Int {
        var k = block
        k *= C1
        k = Integer.rotateLeft(k, ROTATE_K)
        k *= C2

        var mixedHash = hash xor k
        mixedHash = Integer.rotateLeft(mixedHash, ROTATE_HASH)
        return mixedHash * MULTIPLIER + ADDEND
    }

    private fun mixTail(tail: Int): Int {
        var mixed = tail
        mixed *= C1
        mixed = Integer.rotateLeft(mixed, ROTATE_K)
        mixed *= C2
        return mixed
    }

    private fun fmix32(input: Int): Int {
        var h = input
        h = h xor (h ushr 16)
        h *= -0x7a143595 // 0x85ebca6b
        h = h xor (h ushr 13)
        h *= -0x3d4d51cb // 0xc2b2ae35
        h = h xor (h ushr 16)
        return h
    }
}
