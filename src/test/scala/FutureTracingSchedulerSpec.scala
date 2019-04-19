import io.opentracing.mock.MockTracer
import monix.execution.Scheduler
import monix.execution.schedulers.TracingScheduler
import org.mdedetrich.monix.opentracing.TaskLocalScopeManager
import org.scalatest.{AsyncWordSpec, Matchers}

import collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class FutureTracingSchedulerSpec extends AsyncWordSpec with Matchers {
  "GlobalTracer with Future's using TracingScheduler" can {
    "Concurrently sets tags correctly with Future" in {
      val scopeManager                  = new TaskLocalScopeManager()
      val tracer                        = new MockTracer(scopeManager)
      implicit val scheduler: Scheduler = TracingScheduler(ExecutionContext.global)

      def eventualScope = Future {
        tracer.buildSpan("foo").startActive(true)
      }

      val multipleKeyMultipleValues = MultipleKeysMultipleValues.multipleKeyValueGenerator.sample.get

      val futures = for {
        scope <- eventualScope
        tags = multipleKeyMultipleValues.keysAndValues.map { keyValue =>
          Future {
            val activeSpan = tracer.activeSpan()
            activeSpan.setTag(keyValue.key, keyValue.value)
          }
        }
        _ <- Future.sequence(tags)
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
