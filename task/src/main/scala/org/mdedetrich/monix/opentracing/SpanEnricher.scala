package org.mdedetrich.monix.opentracing

import cats.effect.ExitCase
import io.opentracing.Span
import io.opentracing.tag.Tags

/** A [[SpanEnricher]] defines how to enrich a currently active [[Span]]
  * when a Monix [[monix.eval.Task]] completes.
  */

trait SpanEnricher {

  /** A function that defines what to do with a [[Span]] when a Monix
    * [[monix.eval.Task]] completes.
    * NOTE: The closing of the [[Span]] is automatically handled, this
    * function is strictly about adding logs and tags to a [[Span]]. It
    * also means that you should not perform any observable side effects
    * aside from altering the current [[Span]]
    *
    * The default [[SpanEnricher]] does the following thing
    * - [[ExitCase.Completed]] Does nothing
    * - [[ExitCase.Canceled]] Adds a cancelled boolean tag
    * - [[ExitCase.Error]] Sets the Error tag to be true as well
    * as adding the stacktrace to the span logs
    * @param span The current [[Span to add tags and logs
    * @param exitCase How the [[monix.eval.Task]] was completed
    */
  def onTaskCompletion(span: Span, exitCase: ExitCase[Throwable]): Unit
}

object SpanEnricher {
  import java.io.{PrintWriter, StringWriter}
  import java.util.{HashMap => JHashMap}

  /**
   * The default way of enriching a log with a [[Throwable]]
   * @param throwable The exception that was thrown
   * @return a Java map containing the enriched logs
   */
  final def errorLogs(throwable: Throwable): JHashMap[String, AnyRef] = {
    val errorLogs = new JHashMap[String, AnyRef](5)
    errorLogs.put("event", Tags.ERROR.getKey)
    errorLogs.put("error.kind", throwable.getClass.getName)
    errorLogs.put("error.object", throwable)
    errorLogs.put("message", throwable.getMessage)
    val sw = new StringWriter
    throwable.printStackTrace(new PrintWriter(sw))
    errorLogs.put("stack", sw.toString)
    errorLogs
  }

  implicit val defaultSpanEnricher: SpanEnricher = (span: Span, exitCase: ExitCase[Throwable]) =>
    exitCase match {
      case ExitCase.Completed =>
      case ExitCase.Error(e) =>
        Tags.ERROR.set(span, true)
        span.log(errorLogs(e))
      case ExitCase.Canceled =>
        span.setTag("canceled", true)
    }
}
