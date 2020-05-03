package io.propagation.reactor;

import static io.propagation.reactor.Propagation.convert;
import static io.propagation.reactor.Propagation.fromThreadContext;
import static org.junit.Assert.assertEquals;

import io.propagation.thread.ThreadContext;
import java.time.Duration;
import java.util.List;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class FirstTest {

  private static final ThreadContext.Key<String> NAME = ThreadContext.key("name");

  @Test
  public void firstTest() throws Exception {
    List<String> items =
        ThreadContext.current()
            .withValue(NAME, "test")
            .call(
                () ->
                    Flux.just("one", "two", "three")
                        .delaySubscription(Duration.ofSeconds(1))
                        .flatMap(
                            s ->
                                Mono.subscriberContext()
                                    .map(ctx -> s + " " + NAME.get(convert(ctx))))
                        .subscriberContext(fromThreadContext())
                        .collectList()
                        .block());

    assertEquals(3, items.size());
    assertEquals("one test", items.get(0));
    assertEquals("two test", items.get(1));
    assertEquals("three test", items.get(2));
  }
}
