import akka.actor.ActorSystem
import akka.actor.Actor
import akka.http.scaladsl.client._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.Done
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol._
import scala.concurrent.Future
import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import akka.http.scaladsl.model._
import spray.json.DefaultJsonProtocol

  //Classes pour l'api de trajet
  case class Text(text:String)
  case class Name(name:String)
  case class Line(short_name:String,text_color:String,vehicle:Name)
  case class Transit_detail(arrival_stop:Name,arrival_time:Text,departure_stop:Name,departure_time:Text,headsign:String,line:Line,num_stops:Int)
  case class Step(distance:Text,duration:Text,html_instructions:String,travel_mode:String,transit_details:Option[Transit_detail])
  case class Leg(arrival_time:Text,departure_time:Text,distance:Text,duration:Text,end_address:String,start_address:String,steps:List[Step])
  case class Route(legs:List[Leg])
  case class Transit(status:String,routes:List[Route])
  object JsonFormatTransit extends DefaultJsonProtocol with SprayJsonSupport {
  	implicit val nameF = jsonFormat1(Name)
  	implicit val textF = jsonFormat1(Text)
  	implicit val lineF = jsonFormat3(Line)
  	implicit val transit_detailF = jsonFormat7(Transit_detail)
  	implicit val stepF = jsonFormat5(Step)
  	implicit val legF = jsonFormat7(Leg)
  	implicit val routeF = jsonFormat1(Route)
  	implicit val transitF = jsonFormat2(Transit)
  }

  //Classes pour l'échange de messages
  case class Status(msg: String)
  case class Message(msg: String)
  case class Perturbation(ligne: String)
  case class IdMdp(id: String, mdp: String)
  case class TrajetGoogle(trajet: Transit)
  case class DemandeTrajet(origine: String, destination: String)

  //Acteur qui gère un trajet
  class Trajet(depart: String, destination: String, idmdp: Option[IdMdp]) extends Actor {
    val traj = context.actorOf(Props(new apiTrajet()))
    traj ! DemandeTrajet("gare+saint+lazarre+paris+france", "universite+paris+13+villetaneuse+france")
    idmdp match{
      case Some(IdMdp(id,mdp)) => //val traj = context.actorOf(Props(new apiTrajet()))
      case None =>
    }
    def receive = {
      case Status(msg) => println(msg)
      case TrajetGoogle(trajet) => println(trajet)
      case Perturbation(ligne) => val sms = context.actorOf(Props(new Free(idmdp.get)));sms ! Message("Perturbations%20sur%20la%20ligne%20"+ligne)
    }
  }

  //Acteur qui gère l'envoie de SMS
  class Free(idmdp: IdMdp) extends Actor{
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    val duration = Duration(10000, MILLISECONDS)
    def receive = {
      case Message(msg) =>
      val responseFuture: Future[HttpResponse] =
      Http().singleRequest(HttpRequest(uri = "https://smsapi.free-mobile.fr/sendmsg?user="+idmdp.id+"&pass="+idmdp.mdp+"&msg="+msg))
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

  }*/

  class apiTrajet() extends Actor{
    import JsonFormatTransit._
    implicit val system = ActorSystem()
		implicit val materializer = ActorMaterializer()
		implicit val executionContext = system.dispatcher
    val duration = Duration(15000, MILLISECONDS)

    def receive = {
      case DemandeTrajet(origine, destination) =>
        val responseFuture: Future[HttpResponse] =
          Http().singleRequest(HttpRequest(uri = s"https://maps.googleapis.com/maps/api/directions/json?origin=${origine}&destination=${destination}&mode=transit&key=API"))
          val result = Await.result(responseFuture, duration).asInstanceOf[HttpResponse]
          result._1.intValue() match
          {
            case 200 =>{
              val ticker = Unmarshal(result.entity).to[Transit]
              val t = Await.result(ticker,10.second)
              sender ! TrajetGoogle(t)
            }
            case 500 => println("Erreur du serveur, veuillez réessayer")
          }
        }
      }

  object Transport extends App {

    val system = ActorSystem("trajet")

    val transport = system.actorOf(Props(new Trajet("test","test", Some(IdMdp("xxx","yyy")))))
    //transport ! Perturbation("b")
    //transport ! akka.actor.PoisonPill
  }
