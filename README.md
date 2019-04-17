# Monix OpenTracing

Monix OpenTracing adds support for [OpenTracing](https://opentracing.io/)
when using [Monix Task](https://monix.io/). This library provides various
ways of how you can integrate OpenTracing into your application

## Design

Unlike solutions like [this](https://github.com/tabdulradi/puretracing),
Monix Opentracing is designed to work with how the OpenTracing ecosystem
expects `Spans` and `SpanContext`'s to be used (which means its side-effecting).

The reason for this is because of issues experienced when propagating the
context either by using purely functional solutions (such as `Reader`/`ReaderT`)
or using Scala's implicits, which often come down to extensibility/ecosystem
interoperability.

As an example, if you use `Reader`/`ReaderT` or implicits to carry your `Span`/`SpanContext`
around then your **entire** ecosystem has to do the same thing. This means you also have
to be able to modify your downstream dependencies (to use your `SpanContext`) in
order to properly align your traces. 

This works fine if you have complete control over the ecosystem (i.e. the source of all of
your dependencies) but fails otherwise.

To avoid this problem, Monix Opentracing uses `Task` `Local` which is the
equivalent of a Java  `ThreadLocal` (but for `Task`) meaning you don't have to
explicitly pass your `SpanContext` everywhere.


## Installation

Add the following into SBT

```
libraryDependencies ++= List(
  "org.mdedetrich" %% "monix-opentracing" % "0.1.0-SNAPSHOT"
)
```

## Usage

There are various ways to work with OpenTracing, depending on how "automatic" you want the
integration to be.

Firstly in order to use Monix Task you need to enable `TaskLocal` in your
`Task`, there are multiple ways to do this

* You can import the `Task.defaultOptions.enableLocalContextPropagation` implicit and use `runToFutureOpt`
* Applying a transformation `.executeWithOptions(_.enableLocalContextPropagation)` on each Task that uses a Local
* Setting system property `monix.environment.localContextPropagation` to 1

### Using the Scope Manager

OpenTracing provides an abstraction called the
[ScopeManager](https://github.com/opentracing/specification/blob/master/rfc/scope_manager.md) which
is used to mange the scoping of OpenTracing. There is a basic tutorial 
[here](https://opentracing.io/guides/java/scopes/)
which specifies how you can set up a custom scope manager.

monix-opentracing provides two different `ScopeManager`s which are analogous to the ones
provided by opentracing, these are

* `TaskLocalScopeManager` (analogous to `ScopeManager`)
* `AutoFinishTaskLocalScopeManager` (analogous to `AutoFinishScopeManager`)

All you need to do is to configure your `Tracer` to use one of the above `ScopeManager`'s rather then
the default one.
