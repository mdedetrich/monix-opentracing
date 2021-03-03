import io.opentracing.mock.MockTracer
import io.opentracing.noop.NoopTracer
import io.opentracing.util.GlobalTracer
import monix.eval._
import monix.execution.Scheduler
import org.mdedetrich.monix.opentracing.LocalScopeManager
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class BasicSpec extends AsyncWordSpec with Matchers {
  implicit val scheduler: Scheduler                        = Scheduler.traced
  implicit val opts: Task.Options                          = Task.defaultOptions.enableLocalContextPropagation
  override implicit val executionContext: ExecutionContext = scheduler

  "GlobalTracer with Tasks" can {

    "Register MonixTaskLocalScopeManager" in {
      val scopeManager = new LocalScopeManager()
      val mockTracer   = new MockTracer(scopeManager)
      GlobalTracer.registerIfAbsent(mockTracer)
      GlobalTracer.get() should not be an[NoopTracer]
    }

    "Concurrently sets tags correctly" in {
      val scopeManager = new LocalScopeManager()
      val tracer       = new MockTracer(scopeManager)

      val span = tracer.buildSpan("foo").start()

      tracer.activateSpan(span)

      val multipleKeyMultipleValues = MultipleKeysMultipleValues.multipleKeyValueGenerator.sample.get

      val tags = multipleKeyMultipleValues.keysAndValues.map { keyValue =>
        Task {
          val activeSpan = tracer.activeSpan()
          activeSpan.setTag(keyValue.key, keyValue.value)
        }.executeAsync
      }

      val tasks = for {
        _ <- Task.parSequenceUnordered(tags)
        _ <- Task(span.finish())
      } yield ()

      tasks.runToFutureOpt.map { _ =>
        val finishedSpans = tracer.finishedSpans().asScala
        val tags = multipleKeyMultipleValues.keysAndValues.map { keyValue =>
          (keyValue.key, keyValue.value)
        }.toMap

        val finishedTags = finishedSpans.head.tags().asScala.toMap.map { case (k, v) =>
          (k, v.asInstanceOf[String])
        }

        tags shouldBe finishedTags
      }
    }

    "Concurrently make child spans" in {
      val scopeManager = new LocalScopeManager()
      val tracer       = new MockTracer(scopeManager)

      val span = tracer.buildSpan("parent").start()

      tracer.activateSpan(span)

      val multipleKeyMultipleValues = MultipleKeysMultipleValues.multipleKeyValueGenerator.sample.get

      val tags = multipleKeyMultipleValues.keysAndValues.map { keyValue =>
        Task {
          val activeSpan       = tracer.activeSpan()
          val closeSpan        = tracer.buildSpan(keyValue.key).asChildOf(activeSpan)
          val closeSpanStarted = closeSpan.start()
          closeSpanStarted.setTag(keyValue.key, keyValue.value)
          closeSpanStarted.finish()
        }.executeAsync
      }

      val tasks = for {
        _ <- Task.parSequenceUnordered(tags)
        _ <- Task(span.finish())
      } yield ()

      tasks.runToFutureOpt.map { _ =>
        val finishedSpans            = tracer.finishedSpans().asScala
        val (parentSpan, childSpans) = finishedSpans.partition(_.operationName() == "parent")

        val tags = multipleKeyMultipleValues.keysAndValues.map { keyValue =>
          (keyValue.key, keyValue.value)
        }.toMap

        val finishedTags = childSpans.map { span =>
          span.tags().asScala.map { case (k, v) => (k, v.asInstanceOf[String]) }.toList.head
        }.toMap

        childSpans.forall(_.parentId() == parentSpan.head.context().spanId()) shouldBe true

        tags shouldBe finishedTags
      }
    }
  }
}
