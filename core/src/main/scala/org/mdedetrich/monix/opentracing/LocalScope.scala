package org.mdedetrich.monix.opentracing

import io.opentracing.{Scope, Span}

class LocalScope extends Scope {
  private final var scopeManager: LocalScopeManager = _
  private final var wrapped: Span                   = _
  private final var finishOnClose                   = false
  private final var toRestore: LocalScope           = _

  def this(scopeManager: LocalScopeManager, wrapped: Span, finishOnClose: Boolean) {
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
