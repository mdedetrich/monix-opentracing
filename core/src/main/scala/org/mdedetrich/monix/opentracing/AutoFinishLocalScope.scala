package org.mdedetrich.monix.opentracing

import java.util.concurrent.atomic.AtomicInteger

import io.opentracing.{Scope, Span}

class AutoFinishLocalScope(manager: AutoFinishLocalScopeManager, refCount: AtomicInteger, wrapped: Span) extends Scope {
  private val toRestore: AutoFinishLocalScope = manager.tlsScope.get
  manager.tlsScope.update(this)

  private[opentracing] def capture: AutoFinishLocalScope#Continuation = new Continuation

  override def close(): Unit =
    if (this.manager.tlsScope.get eq this) {
      if (this.refCount.decrementAndGet() == 0)
        this.wrapped.finish()
      this.manager.tlsScope.update(toRestore)
    }

  def span: Span = wrapped

  private[opentracing] class Continuation() {
    refCount.incrementAndGet

    def activate = new AutoFinishLocalScope(manager, refCount, wrapped)
  }

}
