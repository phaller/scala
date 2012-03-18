/**
 *  Copyright (C) 2009-2011 Typesafe Inc. <http://www.typesafe.com>
 */
package scala.actors

/**
 * ActorRef configuration object. It represents the subset of Akka Props class. 
 */
case class Props(creator: () ⇒ StashingActor, dispatcher: String) {
  if (dispatcher != "default-stash-dispatcher")
    sys.error("In the migration all dispatchers must be stashing dispatchers.")
    
  /**
   * Returns a new Props with the specified creator set
   *  Scala API
   */
  def withCreator(c: ⇒ StashingActor) = copy(creator = () ⇒ c)
    
}