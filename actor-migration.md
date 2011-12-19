# Scala Actors Migration Guide 

## Introduction
* Akka actors have higher performance and better API than scala actors. Scala is officially switching to Akka Actors.  
* This document is going to ease the code migration from scala actors to akka actors  	

In section Migration we will talk about what is being changed in the Scala distribution and Scala actors code base. 
Then in section "Step by Step guide for Scala Actors" we show individual steps, with working examples, that one needs to take to convert the code to the version compatible with Akka. 
Afterwards, we discuss how to complete the transition and replace the actual actor implementation. In the final chapter we explain the steps that one needs to take in order to stay with current Scala actors implementation. 
      
## Migration
* Scala actors will be moved to scalax. Every project that wants to keep using them can switch to that implementation by following guidelines in section "Staying with Scala Actors".
* Scala actors get the interface that is equivalent to Akka actors.
* Scala actors are evicted from the standard library but preserved in the distribution???? Is this true? 
* Are Akka actors placed in the distribution or they need to be imported separately??? 
* Do we want to keep the actors in the library at all? Why would it be monolithic? What if someone wants to have minimal memory usage? 

## Step by Step Guide to Scala Actors 

### Inheritance of Actor, Reactor and ReplyReactor 

### Instantiation

### Act Method

### Linking and Unlinking 

### Fault Handling 

## Step by Step Guide to Akka Actors

### 1) Changing the Imports

## Staying with Scala Actors