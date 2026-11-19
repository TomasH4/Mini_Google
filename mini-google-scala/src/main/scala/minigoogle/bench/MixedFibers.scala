package minigoogle.bench

import java.util.function.IntConsumer
import zio.{Clock, Promise, Runtime, Unsafe, ZIO}
import zio.stm.TReentrantLock

/** Four reader fibers and one writer publish the index and vocabulary together. */
object MixedFibers:
  def throughput(read: IntConsumer, write: IntConsumer, writes: Int): Double =
    val program = for
      lock <- TReentrantLock.make.commit
      gate <- Promise.make[Nothing, Unit]
      fibers <- ZIO.foreach((0 until 5).toList) { lane =>
        val work =
          if lane == 4 then
            ZIO.foreachDiscard(0 until writes)(n => ZIO.scoped(lock.writeLock *> ZIO.attempt(write.accept(n))))
          else
            ZIO.foreachDiscard(lane until 4 * writes by 4)(n => ZIO.scoped(lock.readLock *> ZIO.attempt(read.accept(n))))
        (gate.await *> work).fork
      }
      start <- Clock.nanoTime
      _ <- gate.succeed(())
      _ <- ZIO.foreachDiscard(fibers)(_.join)
      end <- Clock.nanoTime
    yield 4.0 * writes * 1e9 / (end - start)
    Unsafe.unsafe { implicit unsafe => Runtime.default.unsafe.run(program).getOrThrowFiberFailure() }

