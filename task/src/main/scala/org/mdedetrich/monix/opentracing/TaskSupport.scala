package org.mdedetrich.monix.opentracing

import io.opentracing.util.GlobalTracer
import io.opentracing.{SpanContext, Tracer}
import monix.eval.{Task, TaskLocal}

object TaskSupport {
  private[this] lazy val maybeGlobalTracer = util.Try(Option(GlobalTracer.get())).toOption.flatten

  /**
    *
    * @param task
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

    /**
      * If a [[SpanContext]] is currently available, injects it into the current Task computation.
      * Will use [[GlobalTracer]]
      * @param tracer Which tracer to use
      * @return Task with the [[SpanContext]] injected as a TaskLocal
      */
    def injectCurrentSpan(tracer: Tracer): Task[T] = TaskSupport.injectCurrentSpan(task, tracer)

    /**
      * If a [[SpanContext]] is currently available, injects it into the current Task computation.
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
}
