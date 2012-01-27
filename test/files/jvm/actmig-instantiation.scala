import scala.actors.MigrationSystem._
import scala.actors.Actor._
import scala.actors.{Actor, RichActor, ActorRef}
import java.util.concurrent.{TimeUnit, CountDownLatch}
import scala.collection.mutable.ArrayBuffer

class TestRichActor extends RichActor {
  
  def handle = { case v: Int => Test.append(v); Test.latch.countDown() }

}

object Test {
 val NUMBER_OF_TESTS = 5 

 // used for sorting non-deterministic output
  val buff = ArrayBuffer[Int](0)
  val latch = new CountDownLatch(NUMBER_OF_TESTS)
  val toStop = ArrayBuffer[ActorRef]()

  def append(v: Int) = synchronized {
    buff += v
  }

  def main(args: Array[String]) = {
    // plain scala actor
    val a1 = actor {
      reset {react { case v:Int => Test.append(v); Test.latch.countDown() }}
    }
    a1 ! 100
 
    // simple instantiation
    val a2 = actorOf(new TestRichActor) 
    a2.start()
    a2 ! 200
    
    toStop += a2
    // actor of with scala actor
    val a3 = actorOf(actor{
      reset {react { case v:Int => Test.append(v); Test.latch.countDown() }}
    })        
    a3 ! 300 
     
    // using the manifest    
    val a4 = actorOf[TestRichActor].start()
    a4 ! 400

    toStop += a4
    // deterministic part of a test 
    
    // creation without actorOf 
    try {
     val a3 = new TestRichActor
     a3 ! -1
    } catch {
      case e => println("OK error: " + e)
    }
 
    // actorOf double creation
    try {
     val a3 = actorOf {
       new TestRichActor
       new TestRichActor
     }
     a3 ! -1
    } catch {
      case e => println("OK error: " + e)
    }
    
    // actorOf nesting
    try {
     val a5 = actorOf {
       val a6 = actorOf[TestRichActor]       
       new TestRichActor
     }
     a5.start()
     a5 ! 500
     toStop += a5
    } catch {
      case e => println("Should not throw an exception: " + e)
    }
    
    // output
    latch.await(10, TimeUnit.MILLISECONDS)
    if (latch.getCount() > 0) {
      println("Error: Tasks have not finished!!!")
    }

    buff.sorted.foreach(println)
    toStop.foreach(_.stop())
  }
}
