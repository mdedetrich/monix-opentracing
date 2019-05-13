package org.mdedetrich.monix.opentracing

import java.util.concurrent.atomic.AtomicInteger

import io.opentracing.{Scope, Span}

class AutoFinishLocalScope extends Scope {
  private var manager: AutoFinishLocalScopeManager = _
  private var refCount: AtomicInteger              = _
  private var wrapped: Span                        = _
  private var toRestore: AutoFinishLocalScope      = _

  def this(scopeManager: AutoFinishLocalScopeManager, refCount: AtomicInteger, wrapped: Span) {
    this()
    this.manager = manager
    this.refCount = refCount
    this.wrapped = wrapped
    this.toRestore = manager.tlsScope.get
    manager.tlsScope.update(this)
  }

  class Continuation() {
    refCount.incrementAndGet

    def activate = new AutoFinishLocalScope(manager, refCount, wrapped)
  }

  def capture: Continuation = new Continuation

  override def close(): Unit = {
    if (manager.tlsScope.get != this) return

    if (refCount.decrementAndGet == 0) wrapped.finish()

    manager.tlsScope.update(toRestore)
  }

  override def span(): Span =
    wrapped
}
