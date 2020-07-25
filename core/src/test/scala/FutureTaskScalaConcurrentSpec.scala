import io.opentracing.contrib.concurrent.TracedExecutionContext
import io.opentracing.mock.MockTracer
import monix.eval.Task
import monix.execution.schedulers.AsyncScheduler
import monix.execution.{ExecutionModel, Scheduler, UncaughtExceptionReporter}
import org.mdedetrich.monix.opentracing.LocalScopeManager
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class FutureTaskScalaConcurrentSpec extends AsyncWordSpec with Matchers with BeforeAndAfter {
  implicit val opts: Task.Options = Task.defaultOptions.enableLocalContextPropagation
  val scopeManager                = new LocalScopeManager()
  val tracer                      = new MockTracer(scopeManager)

  before {
    tracer.reset()
  }

  implicit val scheduler: Scheduler = AsyncScheduler(
    Scheduler.DefaultScheduledExecutor,
    new TracedExecutionContext(ExecutionContext.Implicits.global, tracer),
    ExecutionModel.Default,
    UncaughtExceptionReporter.default
  )
  override implicit val executionContext: ExecutionContext = scheduler

  "GlobalTracer with Future's and Task using TracedExecutionContext" can {
    "Concurrently sets tags correctly with Future first" in {

      val eventualScope = Future {
        val span = tracer.buildSpan("foo").start()
        tracer.activateSpan(span)
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
        _     <- Task.parSequenceUnordered(tags)
        _     <- Task { tracer.activeSpan().finish() }
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

    }

    "Concurrently sets tags correctly with Task first" in {

      val taskScope = Task {
        val span = tracer.buildSpan("foo").start()
        tracer.activateSpan(span)
      }

      val multipleKeyMultipleValues = MultipleKeysMultipleValues.multipleKeyValueGenerator.sample.get

      val futures = for {
        scope <- taskScope.runToFutureOpt
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
