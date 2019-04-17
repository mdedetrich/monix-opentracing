import org.scalatest._
import io.opentracing.util.GlobalTracer
import org.mdedetrich.monix.opentracing.TaskLocalScopeManager
import io.opentracing.mock.MockTracer
import io.opentracing.noop.NoopTracer
import monix.eval._
import collection.JavaConverters._
import monix.execution.Scheduler

class BasicSpec extends AsyncWordSpec with Matchers {
  implicit val scheduler: Scheduler = Scheduler.global
  implicit val opts: Task.Options   = Task.defaultOptions.enableLocalContextPropagation

  "GlobalTracer with Tasks" can {

    "Register MonixTaskLocalScopeManager" in {
      val scopeManager = new TaskLocalScopeManager()
      val mockTracer   = new MockTracer(scopeManager)
      GlobalTracer.register(mockTracer)
      GlobalTracer.get() should not be an[NoopTracer]
    }

    "Concurrently sets tags correctly" in {
      val scopeManager = new TaskLocalScopeManager()
      val tracer       = new MockTracer(scopeManager)

      val scope = tracer.buildSpan("foo").startActive(true)

      val multipleKeyMultipleValues = MultipleKeysMultipleValues.multipleKeyValueGenerator.sample.get

      val tags = multipleKeyMultipleValues.keysAndValues.map { keyValue =>
        Task {
          val activeSpan = tracer.activeSpan()
          activeSpan.setTag(keyValue.key, keyValue.value)
        }.executeAsync
      }

      val tasks = for {
        _ <- Task.gatherUnordered(tags)
        _ <- Task { scope.close() }
      } yield ()

      tasks.runToFutureOpt.map { _ =>
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

    "Concurrently make child spans" in {
      val scopeManager = new TaskLocalScopeManager()
      val tracer       = new MockTracer(scopeManager)

      val scope = tracer.buildSpan("parent").startActive(true)

      val multipleKeyMultipleValues = MultipleKeysMultipleValues.multipleKeyValueGenerator.sample.get

      val tags = multipleKeyMultipleValues.keysAndValues.map { keyValue =>
        Task {
          val activeSpan = tracer.activeSpan()
          val closeSpan  = tracer.buildSpan(keyValue.key).asChildOf(activeSpan).startActive(true)
          closeSpan.span().setTag(keyValue.key, keyValue.value)
          closeSpan.close()
        }.executeAsync
      }

      val tasks = for {
        _ <- Task.gatherUnordered(tags)
        _ <- Task { scope.close() }
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