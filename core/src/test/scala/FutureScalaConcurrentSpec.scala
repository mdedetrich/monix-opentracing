import io.opentracing.contrib.concurrent.TracedExecutionContext
import io.opentracing.mock.MockTracer
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class FutureScalaConcurrentSpec extends AsyncWordSpec with Matchers with BeforeAndAfter {
  val tracer = new MockTracer()

  before {
    tracer.reset()
  }

  override implicit val executionContext: ExecutionContext =
    new TracedExecutionContext(ExecutionContext.global, tracer)

  "GlobalTracer with Future's using TracedExecutionContext" can {
    "Concurrently sets tags correctly with Future" in {

      val eventualScope = Future {
        val span = tracer.buildSpan("foo").start()
        tracer.activateSpan(span)
      }

      val multipleKeyMultipleValues = MultipleKeysMultipleValues.multipleKeyValueGenerator.sample.get

      val future = for {
        scope <- eventualScope
        tags = multipleKeyMultipleValues.keysAndValues.map { keyValue =>
          Future {
            val activeSpan = tracer.activeSpan()
            activeSpan.setTag(keyValue.key, keyValue.value)
          }
        }
        _ <- Future.sequence(tags)
        _ <- Future { tracer.activeSpan().finish() }
        _ <- Future { scope.close() }
      } yield ()

      future.map { _ =>
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
