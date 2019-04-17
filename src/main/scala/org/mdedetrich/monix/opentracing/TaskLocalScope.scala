package org.mdedetrich.monix.opentracing

import io.opentracing.{Scope, Span}

class TaskLocalScope extends Scope {
  private final var scopeManager: TaskLocalScopeManager = _
  private final var wrapped: Span                       = _
  private final var finishOnClose                       = false
  private final var toRestore: TaskLocalScope           = _

  def this(scopeManager: TaskLocalScopeManager, wrapped: Span, finishOnClose: Boolean) {
    this()
    this.scopeManager = scopeManager
    this.wrapped = wrapped
    this.finishOnClose = finishOnClose
    this.toRestore = scopeManager.tlScope.get
    scopeManager.tlScope.update(this)
  }

  override def close(): Unit = {
    if (scopeManager.tlScope.get != this) {
      // This shouldn't happen if users call methods in the expected order. Bail out.
      return
    }

    if (finishOnClose) {
      wrapped.finish()
    }

    scopeManager.tlScope.update(toRestore)
  }

  override def span(): Span =
    wrapped
}
