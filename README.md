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

Note: It would be possible for `ThreadContext` to extend `Context`, but this doesn't
make anything more efficient, and makes the transition between the two less explicit.

### Typed keys?

Some existing libraries like gRPC use typed context keys,
while some existing libraries like Reactor do not.

We're interested to hear arguments against typed keys.

But since one of the primary goals of this project is to improve interoperability
across existing libraries' context propagation systems,
the underlying `Context` does not require typed keys.

`ThreadContext` however, does use typed keys.

This satisfies the two general purpose context propagation libraries,
Reactor (untyped, no thread binding) and gRPC (typed, with thread binding).

The other libraries that do thread-bound context propagation, like Armeria and Brave,
appear at first glance to only propagate specific objects, like RequestContext and SpanContext.

So thinking these could use typed keys, but definitely need to do work on this still.

It would be nice if we don't need to add an untyped `ThreadContext` if possible,
but this is also a possibility.

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
