package org.mdedetrich.monix.opentracing

import java.util.concurrent.atomic.AtomicInteger

import io.opentracing.{Scope, ScopeManager, Span}
import monix.execution.misc.Local

class AutoFinishTaskLocalScopeManager extends ScopeManager {
  final val tlsScope = new Local[AutoFinishTaskLocalScope](() => new AutoFinishTaskLocalScope)

  override def activate(span: Span, finishSpanOnClose: Boolean): Scope =
    new AutoFinishTaskLocalScope(this, new AtomicInteger(1), span)

  override def active(): Scope = tlsScope.get

}
