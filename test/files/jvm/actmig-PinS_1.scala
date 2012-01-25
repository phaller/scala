import scala.actors._

import scala.actors.Actor._

object SillyActor {
  val ref = ActorSystem.actorOf[SillyActor]  
}

/* PinS, Listing 32.1: A simple actor
 */
class SillyActor extends Actor {
  def act() {
    for (i <- 1 to 5) {
      println("I'm acting!")
      //Thread.sleep(1000)
      Thread.sleep(100)
    }
  }
}

object SeriousActor {
  val ref = ActorSystem.actorOf[SillyActor]  
}

class SeriousActor extends Actor {
  def act() {
    for (i <- 1 to 5) {
      println("To be or not to be.")
      //Thread.sleep(1000)
      Thread.sleep(100)
    }
  }
}

/* PinS, Listing 32.3: An actor that calls react
 */
object NameResolver extends Actor {
  import java.net.{InetAddress, UnknownHostException}

  def act() {
    react {
      case (name: String, actor: Actor) =>
        actor ! getIp(name)
        act()
      case "EXIT" =>
        println("Name resolver exiting.")
        // quit
      case msg =>
        println("Unhandled message: " + msg)
        act()
    }
  }

  def getIp(name: String): Option[InetAddress] = {
    try {
      Some(InetAddress.getByName(name))
    } catch {
      case _: UnknownHostException => None
    }
  }

}

object Test extends App {

  /* PinS, Listing 32.2: An actor that calls receive
   */
  def makeEchoActor(): Actor = actor {
    while (true) {
      receive {
        case 'stop =>
          exit()
        case msg =>
          println("received message: " + msg)
      }
    }
  }

  /* PinS, page 696
   */
  def makeIntActor(): Actor = actor {
    receive {
      case x: Int => // I only want Ints
        println("Got an Int: " + x)
    }
  }

  /* PinS, page 697
   */
  self ! "hello"
  println(self.receive { case x => x })
  println(self.receiveWithin(100) { case x => x })

  actor {
    self.trapExit = true
    self.link(SillyActor.ref)
    SillyActor.ref.start()

    react {
      case Exit(SillyActor.ref, _) =>
        self.link(SeriousActor.ref)
        SeriousActor.ref.start()
        react {
          case Exit(SeriousActor.ref, _) =>
            // PinS, page 694
            val seriousActor2 = actor {
              for (i <- 1 to 5)
                println("That is the question.")
              //Thread.sleep(1000)
              Thread.sleep(100)
            }

            Thread.sleep(800)
            val echoActor = makeEchoActor()
            self.link(echoActor)
            echoActor ! "hi there"
            echoActor ! 15
            echoActor ! 'stop

            react {
              case Exit(_, _) =>
                val intActor = makeIntActor()
                intActor ! "hello"
                intActor ! math.Pi
                // only the following send leads to output
                intActor ! 12
            }
        }
    }
  }

}
