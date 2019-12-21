package org.mdedetrich.monix.opentracing

import java.util.concurrent.atomic.AtomicInteger

import io.opentracing.{ScopeManager, Span}
import monix.execution.misc.Local

class AutoFinishLocalScopeManager extends ScopeManager {
  final val tlsScope = new Local[AutoFinishLocalScope](() => null)

  override def activate(span: Span): AutoFinishLocalScope =
    new AutoFinishLocalScope(this, new AtomicInteger(1), span)

  override def activeSpan(): Span = {
    val scope = tlsScope.get
    if (scope == null)
      null
    else
      scope.span
  }

  private[opentracing] def captureScope: AutoFinishLocalScope#Continuation = {
    val scope = tlsScope.get
    if (scope == null)
      null
    else
      scope.capture
  }

}
