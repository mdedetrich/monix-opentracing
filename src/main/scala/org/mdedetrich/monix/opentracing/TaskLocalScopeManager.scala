package org.mdedetrich.monix.opentracing

import io.opentracing.{Scope, ScopeManager, Span}
import monix.execution.misc.Local

class TaskLocalScopeManager extends ScopeManager {

  final val tlScope = new Local[TaskLocalScope](() => new TaskLocalScope)

  override def activate(span: Span, finishSpanOnClose: Boolean): Scope =
    new TaskLocalScope(this, span, finishSpanOnClose)

  override def active(): Scope = tlScope.get
}
