import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.Done
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import scala.concurrent.Future
import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import akka.http.scaladsl.model._


  case class Status(msg: String)
  case class Message(msg: String)
  case class Perturbation(ligne: String)
  case class IdMdp(id: String, mdp: String)

  class Trajet(depart: String, destination: String, idmdp: Option[IdMdp]) extends Actor {
    //val trajet = context.actorOf(Props[Trajet])
    def receive = {
      case Status(msg) => println(msg)
      case Perturbation(ligne) =>
        idmdp match {
          case Some(IdMdp(id,mdp)) => val sms = context.actorOf(Props(new Free(id, mdp))); sms ! Message("test")
          case None =>
        }
    }
  }

  class Free(id: String, passwd: String) extends Actor{
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    val duration = Duration(5000, MILLISECONDS)
    def receive = {
      case Message(msg) =>
      val responseFuture: Future[HttpResponse] =
      Http().singleRequest(HttpRequest(uri = "https://smsapi.free-mobile.fr/sendmsg?user="+id+"&pass="+passwd+"&msg="+msg))
      val result = Await.result(responseFuture, duration).asInstanceOf[HttpResponse]
      result._1.intValue() match
      {
        case 200 => sender ! Status("SMS envoyé")
        case 400 => sender ! Status("Vous devez renseigner le login et le mot de passe")
        case 402 => sender ! Status("Trop de SMS ont été envoyés, veuillez attendre")
        case 403 => sender ! Status("Le service n'est pas activé ou le login/mot de passe est incorrect")
        case 500 => sender ! Status("Erreur du serveur, veuillez réessayer")
      }
    }
  }

  /*class apitPerturbation(depart: String, destination: String) extends Actor{

  }

  //def getTrajet(depart: String, arrivee: String, heure: Int, minutes: Int): Future[Done] = ???

  class apiTrajet(ligne: String) extends Actor{

  }*/
  object Transport extends App {

    val system = ActorSystem("trajet")

    val transport = system.actorOf(Props(new Trajet("test","test", Some(IdMdp("xxx","yyy")))))
    //transport ! Perturbation("b")
    //transport ! akka.actor.PoisonPill
  }
