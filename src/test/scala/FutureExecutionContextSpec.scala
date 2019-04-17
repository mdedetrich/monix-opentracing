import io.opentracing.contrib.concurrent.{TracedAutoFinishExecutionContext, TracedExecutionContext}
import io.opentracing.mock.MockTracer
import monix.eval.Task
import monix.execution.{ExecutionModel, Scheduler, UncaughtExceptionReporter}
import monix.execution.schedulers.AsyncScheduler
import org.mdedetrich.monix.opentracing.{AutoFinishTaskLocalScopeManager, TaskLocalScopeManager}
import org.scalatest.{AsyncWordSpec, Matchers}

import collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class FutureExecutionContextSpec extends AsyncWordSpec with Matchers {
  implicit val opts: Task.Options = Task.defaultOptions.enableLocalContextPropagation

  "GlobalTracer with Future's and TracedExecutionContext" can {
    "Concurrently sets tags correctly with Future first" in {
      val scopeManager = new TaskLocalScopeManager()
      val tracer       = new MockTracer(scopeManager)

      implicit val scheduler: Scheduler = AsyncScheduler(
        Scheduler.DefaultScheduledExecutor,
        new TracedExecutionContext(ExecutionContext.Implicits.global, tracer),
        UncaughtExceptionReporter.default,
        ExecutionModel.Default
      )

      val eventualScope = Future {
        tracer.buildSpan("foo").startActive(true)
      }

      val multipleKeyMultipleValues = MultipleKeysMultipleValues.multipleKeyValueGenerator.sample.get

      val tags = multipleKeyMultipleValues.keysAndValues.map { keyValue =>
        Task {
          val activeSpan = tracer.activeSpan()
          activeSpan.setTag(keyValue.key, keyValue.value)
        }.executeAsync
      }

      val tasks = for {
        scope <- Task.fromFuture(eventualScope)
        _     <- Task { tracer.scopeManager().active().span().finish() }
        _     <- Task.gatherUnordered(tags)
        _     <- Task { scope.close() }
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

      true shouldBe true

    }
  }

  "GlobalTracer with Future's and TracedAutoFinishExecutionContext" can {
    "Concurrently sets tags correctly with Future first" in {
      val scopeManager = new AutoFinishTaskLocalScopeManager()
      val tracer       = new MockTracer(scopeManager)

      implicit val scheduler: Scheduler = AsyncScheduler(
        Scheduler.DefaultScheduledExecutor,
        new TracedAutoFinishExecutionContext(ExecutionContext.Implicits.global, tracer),
        UncaughtExceptionReporter.default,
        ExecutionModel.Default
      )

      val eventualScope = Future {
        tracer.buildSpan("foo").startActive(true)
      }

      val multipleKeyMultipleValues = MultipleKeysMultipleValues.multipleKeyValueGenerator.sample.get

      val tags = multipleKeyMultipleValues.keysAndValues.map { keyValue =>
        Task {
          val activeSpan = tracer.activeSpan()
          activeSpan.setTag(keyValue.key, keyValue.value)
        }.executeAsync
      }

      val tasks = for {
        _ <- Task.fromFuture(eventualScope)
        _ <- Task { tracer.scopeManager().active().span().finish() }
        _ <- Task.gatherUnordered(tags)
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

      true shouldBe true

    }
  }

}
