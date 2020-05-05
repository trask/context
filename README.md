# Propagation.io
A standalone context propagation library for Java
that is focused on improving interoperability
across existing context propagation APIs like gRPC Context and Reactor.

# Design questions

### Should the context have a thread-based "current" concept?

Having a thread-based "current" context makes sense in some environments,
but doesn't make sense in others.

Reflecting that, some existing libraries like gRPC Context have this concept,
while some existing libraries like Reactor do not.

So, in order to improve interoperability across existing context propagation APIs,
the current proposal provides both options on top of the same immutable data structure
in order to make transitioning between them efficient
(see `Context` and `ThreadContext`).

### Should the context have typed keys?

Having typed keys seems to be generally preferable
(we are interested to learn more about use cases where they are not!).

However, some existing libraries like gRPC Context use typed keys,
and some existing libraries like Reactor do not.

So, in order to improve interoperability across existing context propagation APIs,
the current proposal provides both options on top of the same immutable data structure
in order to make transitioning between them efficient
(see `Context` and `UntypedContext`).

\[Note: there's no `UntypedThreadContext` yet, we need to investigate more libraries
and hear from more folks to know if this is needed.]

### Should this project support Java 7?

Supporting Java 7 limits some design choices,
but is a requirement for both gRPC and OpenTelemetry.

### Should context be an interface or an abstract class?

Since we are limited to Java 7, `Context`, `ThreadContext`, and `UntypedContext`
have been implemented as abstract classes instead of interfaces
so that their related static methods can be collocated in the same place,
making the API surface feel smaller and improving discoverability.

Alternatively, we could make them interfaces and move the static methods to
corresponding util classes (e.g. `Contexts`, `ThreadContexts`,
and `UntypedContexts`), which would give implementations more flexibility.
