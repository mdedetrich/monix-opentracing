import io.opentracing.contrib.concurrent.{TracedAutoFinishExecutionContext, TracedExecutionContext}
import io.opentracing.mock.MockTracer
import io.opentracing.util.AutoFinishScopeManager
import org.scalatest.{AsyncWordSpec, Matchers}

import collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class FutureScalaConcurrentSpec extends AsyncWordSpec with Matchers {
  "GlobalTracer with Future's using TracedExecutionContext" can {
    "Concurrently sets tags correctly with Future" in {
      val tracer = new MockTracer()
      implicit val executionContext: ExecutionContext =
        new TracedExecutionContext(ExecutionContext.global, tracer)

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
        _ <- Future { tracer.scopeManager().active().span().finish() }
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

  "GlobalTracer with Future's using TracedAutoFinishExecutionContext" can {
    "Concurrently sets tags correctly with Future" in {
      val scopeManager = new AutoFinishScopeManager()
      val tracer = new MockTracer(scopeManager)
      implicit val executionContext: ExecutionContext =
        new TracedAutoFinishExecutionContext(ExecutionContext.global, tracer)

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
