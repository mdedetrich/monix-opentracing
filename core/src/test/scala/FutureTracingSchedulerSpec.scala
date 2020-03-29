import io.opentracing.mock.MockTracer
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.TracingScheduler
import org.mdedetrich.monix.opentracing.LocalScopeManager
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class FutureTracingSchedulerSpec extends AsyncWordSpec with Matchers {
  implicit val opts: Task.Options          = Task.defaultOptions.enableLocalContextPropagation.disableLocalContextIsolateOnRun
  implicit val scheduler: Scheduler        = TracingScheduler(ExecutionContext.global)
  override val executionContext: Scheduler = scheduler

  "GlobalTracer with Future's using TracingScheduler" can {
    "Concurrently sets tags correctly with Future" in {
      val scopeManager = new LocalScopeManager()
      val tracer       = new MockTracer(scopeManager)

      def eventualSpan = Future {
        val span = tracer.buildSpan("foo").start()
        tracer.activateSpan(span)
      }

      val multipleKeyMultipleValues = MultipleKeysMultipleValues.multipleKeyValueGenerator.sample.get

      val futures = for {
        scope <- eventualSpan
        tags = multipleKeyMultipleValues.keysAndValues.map { keyValue =>
          Future {
            val activeSpan = tracer.activeSpan()
            activeSpan.setTag(keyValue.key, keyValue.value)
          }
        }
        _ <- Future.sequence(tags)
        _ <- Future {
              tracer.activeSpan().finish()
            }
        _ <- Future { scope.close() }
      } yield ()

      futures.map { _ =>
        val finishedSpans = tracer.finishedSpans().asScala
        val tags = multipleKeyMultipleValues.keysAndValues.map { keyValue =>
          (keyValue.key, keyValue.value)
        }.toMap

        val finishedTags = finishedSpans.head.tags().asScala.toMap.map {
          case (k, v) => (k, v.asInstanceOf[String])
        }

        tags shouldBe finishedTags
      }
    }
  }
}
