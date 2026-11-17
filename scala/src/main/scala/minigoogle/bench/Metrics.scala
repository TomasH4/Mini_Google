package minigoogle.bench

import java.lang.management.{ManagementFactory, MemoryType}
import scala.jdk.CollectionConverters.*

/** Resumen de latencias en milisegundos. Percentiles por rango más cercano (nearest-rank). */
final case class LatencyStats(count: Int, meanMs: Double, p50Ms: Double, p95Ms: Double, p99Ms: Double, maxMs: Double)

object LatencyStats:
  val empty: LatencyStats = LatencyStats(0, 0, 0, 0, 0, 0)

  def fromNanos(samples: Seq[Long]): LatencyStats =
    if samples.isEmpty then empty
    else
      val sorted = samples.sorted.toVector
      def percentile(p: Double): Double =
        toMillis(sorted(math.max(0, math.ceil(p / 100 * sorted.size).toInt - 1)))
      LatencyStats(
        count = sorted.size,
        meanMs = toMillis(sorted.sum) / sorted.size,
        p50Ms = percentile(50),
        p95Ms = percentile(95),
        p99Ms = percentile(99),
        maxMs = toMillis(sorted.last)
      )

  def toMillis(nanos: Long): Double = nanos / 1e6

object Stats:
  def median(values: Seq[Double]): Double =
    if values.isEmpty then 0.0
    else
      val sorted = values.sorted
      val mid = sorted.size / 2
      if sorted.size % 2 == 1 then sorted(mid) else (sorted(mid - 1) + sorted(mid)) / 2

/** Contadores acumulados del recolector de basura (todas las generaciones). */
final case class GcSnapshot(collections: Long, millis: Long):
  def -(earlier: GcSnapshot): GcSnapshot = GcSnapshot(collections - earlier.collections, millis - earlier.millis)

/** Mediciones de memoria del heap de la JVM vía JMX. */
object Memory:
  private val memoryBean = ManagementFactory.getMemoryMXBean
  private def heapPools = ManagementFactory.getMemoryPoolMXBeans.asScala.filter(_.getType == MemoryType.HEAP)

  /** Heap ocupado después de forzar varias recolecciones: aproxima la memoria realmente retenida. */
  def settledHeapUsed(): Long =
    (1 to 3).foreach { _ =>
      System.gc()
      Thread.sleep(30)
    }
    memoryBean.getHeapMemoryUsage.getUsed

  def resetPeaks(): Unit = heapPools.foreach(_.resetPeakUsage())

  /** Suma de los picos de cada zona del heap desde el último `resetPeaks` (cota superior del pico real). */
  def peakHeapUsed(): Long = heapPools.map(_.getPeakUsage.getUsed).sum

  def gc(): GcSnapshot =
    ManagementFactory.getGarbageCollectorMXBeans.asScala.foldLeft(GcSnapshot(0, 0)) { (acc, bean) =>
      GcSnapshot(acc.collections + math.max(0, bean.getCollectionCount), acc.millis + math.max(0, bean.getCollectionTime))
    }
