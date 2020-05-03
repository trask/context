# Propagation.io
This is a work in progress. Input is super appreciated!

## Goal
Provide a standalone context propagation library for Java
that is focused on improving interoperability
across existing libraries' context propagation systems.

## Design options

### Should we have a thread-based "current" context?

Having a thread-based "current" context makes sense in some environments,
but doesn't make sense in others.

Some existing libraries like gRPC have a thread-based "current" context,
while some existing libraries like Reactor do not.

Since the goal is to improve interoperability across existing libraries'
context propagation systems, the current proposal provides both:

* `Context` - no concept of thread binding
* `ThreadContext` - has various methods to support thread binding

The default `ThreadContext` implementation uses `Context` to store state, which makes
transitioning between the two efficient.

`ThreadContext` only extends `Context` so that users who are using `ThreadContext`
can pass `ThreadContext.current()` directly to APIs that accept `Context`.

### Typed keys?

Some existing libraries like gRPC use typed context keys,
while some existing libraries like Reactor do not.

This project has a preference for typed keys.

But since one of the primary goals of this project is to improve interoperability
across existing libraries' context propagation systems,
it provides an `UntypedContext` that does not require typed keys,
and uses the same underlying storage as `Context`,
which makes transitioning between the two efficient.

### Java 7?

Supporting Java 7 limits some API design choices,
but is a requirement for both gRPC and OpenTelemetry.

### Interface or Abstract Class?

Given that we are limited to Java 7:

Should `Context` and `ThreadContext` be interfaces with corresponding static methods in
something like `Contexts` and `ThreadContexts`?
Interfaces give more flexibility to implementations.

Or should they be abstract classes so that static methods can be collocated?
Collocated static methods are more easily discoverable.

(currently implemented as abstract classes)
