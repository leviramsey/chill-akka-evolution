import akka.actor.typed.{ ActorSystem, Behavior, RecipientRef }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl._
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

trait ChillSerializable

object Model {
  case class Guest(name: String)
}

object HotelRoom {
  import Model.Guest

  sealed trait Command

  case class CheckIn(guest: Guest, replyTo: RecipientRef[Boolean]) extends Command
  case class CheckOut(guest: Guest, replyTo: RecipientRef[Boolean]) extends Command

  sealed trait Event extends ChillSerializable

  case class GuestCheckedIn(guest: Guest) extends Event
  case object GuestCheckedOut extends Event

  sealed trait State

  case object Vacant extends State
  case class Occupied(guest: Guest) extends State

  private def replyFalse(to: RecipientRef[Boolean]) = Effect.none[Event, State].thenRun(_ => to ! false)

  def apply(roomNumber: Int): Behavior[Command] = {
    require(roomNumber > 0)

    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId(s"room:$roomNumber"),
      emptyState = Vacant,
      commandHandler = { (state, cmd) =>
        cmd match {
          case CheckIn(guest, replyTo) =>
            if (state == Vacant) Effect.persist(GuestCheckedIn(guest)).thenRun(_ => replyTo ! true)
            else replyFalse(replyTo)

          case CheckOut(guest, replyTo) =>
            state match {
              case Occupied(g) if g == guest => Effect.persist(GuestCheckedOut).thenRun(_ => replyTo ! true)
              case _ => replyFalse(replyTo)
            }
        }
      },
      eventHandler = { (state, evt) =>
        evt match {
          case GuestCheckedIn(guest) => Occupied(guest)
          case GuestCheckedOut => Vacant
        }
      }
    )
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    import akka.actor.typed.scaladsl.AskPattern._
    import HotelRoom._
    import Model.Guest

    // It's an artisanal, very small-batch hotel, OK?
    val hotelRoom = ActorSystem(HotelRoom(100), "astoria-walledoff")

    import hotelRoom.executionContext

    implicit val timeout: Timeout = 5.seconds
    implicit val scheduler = hotelRoom.scheduler

    val guestOne = Guest("José de San Martín")
    val guestTwo = Guest("Manuel Belgrano")

    val checkInFut = hotelRoom.ask(CheckIn(guestOne, _))

    checkInFut.flatMap { response =>
      if (response) {
        println(s"Checked in $guestOne, now time to check out")

        hotelRoom.ask(CheckOut(guestOne, _))
      } else Future.unit
    }.flatMap { _ =>
      hotelRoom.terminate()

      hotelRoom.whenTerminated
    }
  }
}
