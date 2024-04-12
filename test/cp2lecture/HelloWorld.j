.class public HelloWorld
.super BoardBase

.method public <init>()V
   aload_0
   invokenonvirtual BoardBase/<init>()V
   return
.end method

.method public static main([Ljava/lang/String;)V
   .limit stack 99
   .limit locals 99

    invokestatic ioPlus/printHelloWorld()V

   return
.end method