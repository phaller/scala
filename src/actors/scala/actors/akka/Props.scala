/**
 *  Copyright (C) 2009-2011 Typesafe Inc. <http://www.typesafe.com>
 */
package scala.actors
import collection.immutable.Stack
import scala.util.{Duration, Timeout}

/**
 * ActorRef configuration object, this is threadsafe and fully sharable
 *
 * Props() returns default configuration
 * 
 */
object Props {
  import scala.actors.FaultHandlingStrategy._

  final val defaultCreator: () ⇒ StashingActor = () ⇒ throw new UnsupportedOperationException("No actor creator specified!")
  final val defaultTimeout: Timeout = Timeout(Duration.MinusInf)
  // implement it in a different way
  final val defaultDecider: Decider = {
//    case _: ActorInitializationException ⇒ Stop
//    case _: ActorKilledException         ⇒ Stop
    case _: Exception                    ⇒ Restart
    case _                               ⇒ Escalate
  }
  
  final val defaultFaultHandler: FaultHandlingStrategy = OneForOneStrategy(defaultDecider, None, None)

  /**
   * The default Props instance, uses the settings from the Props object starting with default*
   */
  final val default = new Props()

  /**
   * Returns a cached default implementation of Props
   */
  def apply(): Props = default  

  /**
   * Returns a Props that has default values except for "creator" which will be a function that creates an instance
   * of the supplied type using the default constructor
   */
  def apply[T <: StashingActor: ClassManifest]: Props =
    default.withCreator(implicitly[ClassManifest[T]].erasure.asInstanceOf[Class[_ <: StashingActor]].newInstance)

  /**
   * Returns a Props that has default values except for "creator" which will be a function that creates an instance
   * of the supplied class using the default constructor
   */
  def apply(actorClass: Class[_ <: StashingActor]): Props =
    default.withCreator(actorClass.newInstance)

  // TODO (VJ) how can you create actor
  def apply(faultHandler: FaultHandlingStrategy): Props =
    apply(new StashingActor { def receive = { case _ ⇒ }}).withFaultHandler(faultHandler)
    
  /**
   * Returns a Props that has default values except for "creator" which will be a function that creates an instance
   * using the supplied thunk
   */
  def apply(creator: ⇒ StashingActor): Props = default.withCreator(creator)
}

/**
 * ActorRef configuration object, this is thread safe and fully sharable
 */
case class Props(creator: () ⇒ StashingActor = Props.defaultCreator,                 
                 timeout: Timeout = Props.defaultTimeout,
                 faultHandler: FaultHandlingStrategy = Props.defaultFaultHandler) {
  
  /**
   * Returns a new Props with the specified creator set
   *  Scala API
   */
  def withCreator(c: ⇒ StashingActor) = copy(creator = () ⇒ c)

  /**
   * Returns a new Props with the specified timeout set
   * Java API
   */
  def withTimeout(t: Timeout) = copy(timeout = t)
  
  /**
   * Returns a new Props with the specified faulthandler set
   * Java API
   */
  def withFaultHandler(f: FaultHandlingStrategy) = copy(faultHandler = f)
    
}