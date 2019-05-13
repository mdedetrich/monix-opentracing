package org.mdedetrich.monix.opentracing

import io.opentracing.{Scope, ScopeManager, Span}
import monix.execution.misc.Local

class LocalScopeManager extends ScopeManager {

  final val tlScope = new Local[LocalScope](() => new LocalScope)

  override def activate(span: Span, finishSpanOnClose: Boolean): Scope =
    new LocalScope(this, span, finishSpanOnClose)

  override def active(): Scope = tlScope.get
}
