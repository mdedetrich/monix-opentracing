package org.mdedetrich.monix.opentracing

import io.opentracing.util.GlobalTracer
import io.opentracing.{Scope, Span, SpanContext, Tracer}
import monix.eval.{Task, TaskLocal}

import scala.util.control.NonFatal

object TaskSupport {
  private[this] def maybeGlobalTracer: Option[Tracer] =
    try {
      val globalTracer = GlobalTracer.get()
      Option(globalTracer)
    } catch {
      case NonFatal(_) => None
    }

  /** @param task
    * @param tracer
    * @tparam T
    * @return
    */
  def injectCurrentSpan[T](task: Task[T], tracer: Tracer): Task[T] =
    task.flatMap { currentValue =>
      for {
        _ <- {
          val maybeSpan = Option(tracer.activeSpan().context())
          maybeSpan match {
            case Some(spanContext) =>
              TaskLocal(Some(spanContext): Option[SpanContext])
            case None =>
              TaskLocal(Option.empty[SpanContext])
          }
        }
      } yield currentValue
    }

  implicit final class OpenTracingTaskSupport[T](task: Task[T]) {

    /** If a [[SpanContext]] is currently available, injects it into the current Task computation.
      * @param tracer Which tracer to use
      * @return Task with the [[SpanContext]] injected as a TaskLocal
      */
    def injectCurrentSpan(tracer: Tracer): Task[T] = TaskSupport.injectCurrentSpan(task, tracer)

    /** If a [[SpanContext]] is currently available, injects it into the current Task computation.
      * Will use [[GlobalTracer]]
      * @return Task with the [[SpanContext]] injected as a TaskLocal
      */
    def injectCurrentSpan(): Task[T] =
      task.flatMap { _ =>
        maybeGlobalTracer match {
          case Some(tracer) => TaskSupport.injectCurrentSpan(task, tracer)
          case None         => task
        }
      }
  }

  implicit final class OpenTracingTaskObjectSupport(taskObject: Task.type) {
    /**
      * Allows you to execute a Task with a [[Span]]
      * @param block The task to execute with the provided span which is either the current [[Span]] or [[acquireSpan]]
      * @param acquireSpan How to create a [[Span]] if there isn't a [[Span]] on the current Task
      * @return
      */
    def withSpan[A](block: Span => Task[A], acquireSpan: Task[Span])(implicit spanEnricher: SpanEnricher): Task[A] =
      Task(Option(GlobalTracer.get().activeSpan()))
        .flatMap {
          case Some(span) => Task.pure(AcquireSpanContext.ExistingSpan(span))
          case None =>
            acquireSpan.map { span =>
              val scope = GlobalTracer.get().activateSpan(span)
              AcquireSpanContext.NewSpan(span, scope)
            }
        }
        .bracketCase { spanContext =>
          block(spanContext.span)
        } { case (spanContext, exitCase) =>
          Task {
            spanEnricher.onTaskCompletion(spanContext.span, exitCase)
            spanContext.span.finish()
            spanContext match {
              case context: AcquireSpanContext.NewSpan =>
                context.scope.close()
              case _ =>
            }
          }
        }

    //TODO
    def withMaybeSpan[A](block: Option[Span] => Task[A])(implicit spanEnricher: SpanEnricher): Task[A] =
      Task(Option(GlobalTracer.get().activeSpan())).bracketCase(block) { case (maybeSpan, exitCase) =>
        Task {
          maybeSpan.foreach { span =>
            spanEnricher.onTaskCompletion(span, exitCase)
            span.finish()
          }
        }
      }
  }
}

private[opentracing] sealed abstract class AcquireSpanContext(val span: Span) extends Serializable with Product
private[opentracing] object AcquireSpanContext {
  final case class ExistingSpan(override val span: Span)          extends AcquireSpanContext(span)
  final case class NewSpan(override val span: Span, scope: Scope) extends AcquireSpanContext(span)
}
