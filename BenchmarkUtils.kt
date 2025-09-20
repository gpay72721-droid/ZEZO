package com.example.mobilefaultdetector

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.measureNanoTime
import java.io.File

object BenchmarkUtils {
    fun cpuSingleThreadBenchmark(limit: Int = 100_000): Double {
        fun isPrime(n: Int): Boolean {
            if (n < 2) return false
            var i = 2
            while (i * i <= n) {
                if (n % i == 0) return false
                i++
            }
            return true
        }
        val time = measureNanoTime {
            var count = 0
            for (i in 2..limit) if (isPrime(i)) count++
        }
        val seconds = time / 1e9
        return (limit / seconds)
    }

    suspend fun cpuMultiThreadBenchmark(limitPerThread: Int = 50_000, threads: Int = 4): Double = withContext(Dispatchers.Default) {
        val jobs = mutableListOf<kotlinx.coroutines.Deferred<Double>>()
        repeat(threads) {
            jobs.add(kotlinx.coroutines.GlobalScope.async { cpuSingleThreadBenchmark(limitPerThread) })
        }
        val results = jobs.map { it.await() }
        results.sum()
    }

    fun storageWriteReadBenchmark(context: Context, fileName: String = "bench.tmp", sizeMB: Int = 10): Pair<Double, Double> {
        val file = File(context.cacheDir, fileName)
        val bytes = ByteArray(1024 * 1024) { 0x55.toByte() }
        val writeTime = measureNanoTime {
            file.outputStream().use { out ->
                repeat(sizeMB) { out.write(bytes) }
                out.flush()
            }
        }
        val writeSec = writeTime / 1e9
        val writeMBs = sizeMB / writeSec
        val readTime = measureNanoTime {
            file.inputStream().use { inp ->
                while (inp.read(bytes) != -1) {}
            }
        }
        val readSec = readTime / 1e9
        val readMBs = sizeMB / readSec
        file.delete()
        return Pair(writeMBs, readMBs)
    }
}
