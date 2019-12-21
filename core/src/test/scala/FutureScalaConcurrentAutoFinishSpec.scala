import io.opentracing.contrib.concurrent.{AutoFinishScopeManager, TracedAutoFinishExecutionContext}
import io.opentracing.mock.MockTracer
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class FutureScalaConcurrentAutoFinishSpec extends AsyncWordSpec with Matchers with BeforeAndAfter {
  val scopeManager = new AutoFinishScopeManager()
  val tracer       = new MockTracer(scopeManager)

  before {
    tracer.reset()
  }

  override implicit val executionContext: ExecutionContext =
    new TracedAutoFinishExecutionContext(ExecutionContext.global, tracer)

  "GlobalTracer with Future's using TracedAutoFinishExecutionContext" can {
    "Concurrently sets tags correctly with Future" ignore {

      def eventualScope = Future {
        val span = tracer.buildSpan("foo").start()
        tracer.activateSpan(span)
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
