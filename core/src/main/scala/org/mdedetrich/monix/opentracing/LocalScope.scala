package org.mdedetrich.monix.opentracing

import io.opentracing.{Scope, Span}

class LocalScope(scopeManager: LocalScopeManager, wrapped: Span) extends Scope {
  private final var toRestore: LocalScope = _

  this.toRestore = scopeManager.tlScope.get
  scopeManager.tlScope.update(this)

  override def close(): Unit = {
    if (scopeManager.tlScope.get != this) {
      // This shouldn't happen if users call methods in the expected order. Bail out.
      return
    }
    scopeManager.tlScope.update(toRestore)
  }

  def span(): Span =
    wrapped
}
