package org.mdedetrich.monix.opentracing

import java.util.concurrent.atomic.AtomicInteger

import io.opentracing.{Scope, ScopeManager, Span}
import monix.execution.misc.Local

class AutoFinishLocalScopeManager extends ScopeManager {
  final val tlsScope = new Local[AutoFinishLocalScope](() => new AutoFinishLocalScope)

  override def activate(span: Span, finishSpanOnClose: Boolean): Scope =
    new AutoFinishLocalScope(this, new AtomicInteger(1), span)

  override def active(): Scope = tlsScope.get

}
