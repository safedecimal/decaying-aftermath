package io.github.safedecimal.decayingaftermath.compat;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
class CompatibilityMixinsTest {
 private static Map<String,List<Map<String,Object>>> annotations(String name)throws IOException{
  Map<String,List<Map<String,Object>>> result=new HashMap<>();
  try(InputStream in=CompatibilityMixinsTest.class.getResourceAsStream("/"+name.replace('.','/')+".class")){
   assertNotNull(in);new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9){
    @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){return new MethodVisitor(Opcodes.ASM9){
     @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible){Map<String,Object> fields=new HashMap<>();result.computeIfAbsent(descriptor,k->new ArrayList<>()).add(fields);return collector(fields);}
    };}
   },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
  }return result;
 }
 private static AnnotationVisitor collector(Map<String,Object> fields){return new AnnotationVisitor(Opcodes.ASM9){
  @Override public void visit(String name,Object value){fields.put(name,value);}
  @Override public AnnotationVisitor visitArray(String name){List<Object> items=new ArrayList<>();fields.put(name,items);return new AnnotationVisitor(Opcodes.ASM9){@Override public void visit(String name,Object value){items.add(value);}@Override public AnnotationVisitor visitAnnotation(String name,String desc){Map<String,Object> item=new HashMap<>();items.add(item);return collector(item);}};}
  @Override public AnnotationVisitor visitAnnotation(String name,String descriptor){Map<String,Object> nested=new HashMap<>();fields.put(name,nested);return collector(nested);}
 };}
 @Test void primaryDamageHookWrapsOriginalInsteadOfCompetingNativeRedirect()throws Exception{
  var a=annotations("io.github.safedecimal.decayingaftermath.mixin.PlayerBodyMixin");assertFalse(a.containsKey("Lorg/spongepowered/asm/mixin/injection/Redirect;"));
  var w=a.get("Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;");assertNotNull(w);assertEquals(List.of("actuallyHurt"),w.getFirst().get("method"));
 }
 @Test void nativeFatalFlagUsesActualBodyDeathNeverMirroredHp()throws Exception{
  Set<String> calls=new HashSet<>();Set<String> fields=new HashSet<>();
  try(InputStream in=CompatibilityMixinsTest.class.getResourceAsStream("/io/github/safedecimal/decayingaftermath/compat/OptionalMedicalCompatibility.class")){
   assertNotNull(in);new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9){@Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){if(!name.equals("emitCruorHit"))return null;return new MethodVisitor(Opcodes.ASM9){@Override public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){calls.add(owner+"."+name);}@Override public void visitFieldInsn(int op,String owner,String name,String desc){fields.add(owner+"."+name);}};}},ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
  }
  assertTrue(fields.contains("io/github/safedecimal/decayingaftermath/medical/BodySnapshot$Mode.DEAD"));assertTrue(calls.contains("io/github/safedecimal/decayingaftermath/medical/BodySnapshot.mode"));
  assertFalse(calls.stream().anyMatch(c->c.endsWith(".getHealth")));
 }
 @Test void optionalSendDescriptorMatchesByteInspectedNativeApi()throws Exception{
  var a=annotations("io.github.safedecimal.decayingaftermath.compat.mixin.CruorNetworkCompatMixin");var i=a.get("Lorg/spongepowered/asm/mixin/injection/Inject;").getFirst();
  assertEquals(List.of("send(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;FZ)V"),i.get("method"));assertEquals(false,i.get("remap"));assertEquals(true,i.get("cancellable"));
 }
 @Test void onlyNativeBleedFallbackReadsAreWrappedStainHealingIsPreserved()throws Exception{
  var a=annotations("io.github.safedecimal.decayingaftermath.compat.mixin.CruorBodyBloodCompatMixin");var w=a.get("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;").getFirst();
  assertEquals(List.of("tick(Lnet/minecraft/client/multiplayer/ClientLevel;JLnet/minecraft/world/phys/Vec3;)V"),w.get("method"));
  List<?> ats=(List<?>)w.get("at");assertEquals(2,ats.size());assertEquals(0,((Map<?,?>)ats.get(0)).get("ordinal"));assertEquals(2,((Map<?,?>)ats.get(1)).get("ordinal"));
  for(Object at:ats)assertEquals("Lnet/minecraft/world/entity/LivingEntity;getHealth()F",((Map<?,?>)at).get("target"));
 }
}
