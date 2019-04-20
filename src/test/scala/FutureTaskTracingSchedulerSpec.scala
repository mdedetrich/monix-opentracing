import io.opentracing.mock.MockTracer
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.TracingScheduler
import org.mdedetrich.monix.opentracing.{AutoFinishTaskLocalScopeManager, TaskLocalScopeManager}
import org.scalatest.{AsyncWordSpec, Matchers}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class FutureTaskTracingSchedulerSpec extends AsyncWordSpec with Matchers {
  implicit val opts: Task.Options                 = Task.defaultOptions.enableLocalContextPropagation
  implicit val scheduler: Scheduler               = TracingScheduler(ExecutionContext.global)
  override def executionContext: ExecutionContext = scheduler

  "GlobalTracer with Future's using TracingScheduler" can {
    "Concurrently sets tags correctly with Future first" ignore {
      val scopeManager = new TaskLocalScopeManager()
      val tracer       = new MockTracer(scopeManager)

      def eventualScope = Future {
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

    }

    "Concurrently sets tags correctly with Task first" ignore {
      val scopeManager = new TaskLocalScopeManager()
      val tracer       = new MockTracer(scopeManager)

      val taskScope = Task {
        tracer.buildSpan("foo").startActive(true)
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

  "GlobalTracer with Future's using TracingScheduler + AutoFinishTaskLocalScopeManager" can {
    "Concurrently sets tags correctly with Future first" ignore {
      val scopeManager = new AutoFinishTaskLocalScopeManager()
      val tracer       = new MockTracer(scopeManager)

      def eventualScope = Future {
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

    }
  }
}
