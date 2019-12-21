package org.mdedetrich.monix.opentracing

import io.opentracing.{Scope, ScopeManager, Span}
import monix.execution.misc.Local

class LocalScopeManager extends ScopeManager {

  final val tlScope = new Local[LocalScope](() => null)

  override def activate(span: Span): Scope =
    new LocalScope(this, span)

  override def activeSpan(): Span = {
    val scope = tlScope.get
    if (scope == null)
      null
    else
      scope.span()
  }
}
