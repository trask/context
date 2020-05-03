package io.propagation.reactor;

import io.propagation.Context;
import io.propagation.thread.ThreadContext;

public class Propagation {

  private static final Object KEY = new Object();
  private static final ThreadContext.Key<reactor.util.context.Context> TYPED_KEY =
      ThreadContext.keyWithDefault("reactorcontext", reactor.util.context.Context.empty());

  public static reactor.util.context.Context fromThreadContext() {
    return convert(ThreadContext.current());
  }

  public static ThreadContext toThreadContext(reactor.util.context.Context context) {
    return ThreadContext.wrap(convert(context));
  }

  public static reactor.util.context.Context convert(Context context) {
    return TYPED_KEY.get(context).put(KEY, context);
  }

  public static Context convert(reactor.util.context.Context context) {
    return context.getOrDefault(KEY, Context.empty()).withValue(TYPED_KEY, context);
  }

  private Propagation() {}
}
