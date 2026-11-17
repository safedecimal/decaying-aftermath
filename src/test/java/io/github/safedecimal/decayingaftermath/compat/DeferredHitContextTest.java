package io.github.safedecimal.decayingaftermath.compat;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
class DeferredHitContextTest {
 @Test void emitsOnceAfterCompletedBodyEvaluationNotBefore() {
  List<DeferredHitContext.Hit<Object,Object>> sent=new ArrayList<>();var chain=new DeferredHitContext<Object,Object>(sent::add);Object player=new Object(),source=new Object();
  try(var frame=chain.begin(player,source)){assertSame(source,chain.source(player));assertTrue(chain.capture(player,source,3));assertTrue(chain.capture(player,source,3));assertTrue(sent.isEmpty());frame.complete();}
  assertEquals(1,sent.size());assertSame(source,sent.getFirst().source());assertEquals(3,sent.getFirst().damage());assertNull(chain.source(player));
 }
 @Test void sourceAndVictimAreIdentityLocked() {
  var chain=new DeferredHitContext<Object,Object>(hit->fail());Object player=new Object(),source=new Object();
  try(var frame=chain.begin(player,source)){assertNull(chain.source(new Object()));assertFalse(chain.capture(new Object(),source,3));assertFalse(chain.capture(player,new Object(),3));frame.complete();}
 }
 @Test void exceptionAbortsQueuedHitAndClearsScope() {
  List<Object> sent=new ArrayList<>();var chain=new DeferredHitContext<Object,Object>(sent::add);Object player=new Object(),source=new Object();
  assertThrows(IllegalStateException.class,()->{try(var frame=chain.begin(player,source)){assertTrue(chain.capture(player,source,2));throw new IllegalStateException("original failed");}});
  assertTrue(sent.isEmpty());assertNull(chain.source(player));
 }
 @Test void nestedScopesRestoreOuterSourceAndNativeReplayCannotRecapture() {
  AtomicReference<DeferredHitContext<Object,Object>> holder=new AtomicReference<>();List<Object> sent=new ArrayList<>();
  holder.set(new DeferredHitContext<>(hit->{sent.add(hit);assertFalse(holder.get().capture(hit.victim(),hit.source(),hit.damage()));}));var chain=holder.get();Object player=new Object(),a=new Object(),b=new Object();
  try(var outer=chain.begin(player,a)){try(var inner=chain.begin(player,b)){assertSame(b,chain.source(player));chain.capture(player,b,4);inner.complete();}assertSame(a,chain.source(player));chain.capture(player,a,1);outer.complete();}
  assertEquals(2,sent.size());assertNull(chain.source(player));
 }
 @Test void rejectedAmountsDoNotCreateCosmeticHits() {
  var chain=new DeferredHitContext<Object,Object>(hit->fail());Object player=new Object(),source=new Object();
  try(var frame=chain.begin(player,source)){for(float bad:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY})assertFalse(chain.capture(player,source,bad));frame.complete();}
 }
 @Test void replayExceptionStillClearsThreadLocalAndNextChainWorks() {
  AtomicInteger count=new AtomicInteger();var chain=new DeferredHitContext<Object,Object>(hit->{if(count.incrementAndGet()==1)throw new IllegalStateException("native failure");});Object player=new Object(),source=new Object();
  assertThrows(IllegalStateException.class,()->{try(var frame=chain.begin(player,source)){chain.capture(player,source,1);frame.complete();}});assertNull(chain.source(player));
  try(var frame=chain.begin(player,source)){assertTrue(chain.capture(player,source,1));frame.complete();}assertEquals(2,count.get());
 }
 @Test void contextDoesNotLeakToOtherThread() {
  var chain=new DeferredHitContext<Object,Object>(hit->{});Object player=new Object(),source=new Object();
  try(var frame=chain.begin(player,source)){assertNull(CompletableFuture.supplyAsync(()->chain.source(player)).join());frame.complete();}
 }
 @Test void repeatedCloseDoesNotDuplicateNativeSend() {
  AtomicInteger count=new AtomicInteger();var chain=new DeferredHitContext<Object,Object>(hit->count.incrementAndGet());Object player=new Object(),source=new Object();
  var frame=chain.begin(player,source);chain.capture(player,source,1);frame.complete();frame.close();frame.close();assertEquals(1,count.get());
 }
}
