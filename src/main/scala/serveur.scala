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
import java.util.Calendar
import java.text.SimpleDateFormat
import Perturbations._


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
  //case object Heure

  //Acteur qui gère un trajet
  class Trajet(depart: String, destination: String, idmdp: Option[IdMdp]) extends Actor {
    val system = akka.actor.ActorSystem("system")
    val simpDate = new SimpleDateFormat("hh:mm");
    val now = (simpDate.format(Calendar.getInstance().getTime()))
    //var heure = context.children.toList(0) ! Heure
    //println(heure)
    val traj = context.actorOf(Props(new apiTrajet()))
    traj ! DemandeTrajet(depart, destination)
    idmdp match{
      case Some(IdMdp(id,mdp)) =>
        //context.system.scheduler.scheduleOnce(Duration.create(5, TimeUnit.SECONDS), self, akka.actor.PoisonPill, context.system.dispatcher, null);
        //self ! akka.actor.PoisonPill
      case None => self ! akka.actor.PoisonPill
    }
    def receive = {
      case Status(msg) => println(msg)
      case TrajetGoogle(trajet) =>
        trajet.routes.foreach{_.legs.foreach{_.steps.foreach{_.transit_details.foreach{x => context.actorOf(Props(new apitPerturbation(x.line.short_name, x.departure_time, x.line.vehicle)))}}}};
        //heure=trajet.routes(0).legs(0).arrival_time.text.dropRight(2);//(context.children.toList(0) ! Heure
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
      self ! akka.actor.PoisonPill
    }
  }

  class apitPerturbation(ligne: String, heure: Text, nom: Name) extends Actor{
    val typeLigne = getType(nom.name)
    val nomLigne = ligne.replaceAll("RER " , "")
    val heureMax = heure.text

    println("Ligne surveillée :"+nomLigne)

    // On récupère le bon réseau (train, bus, tramway, metro)
    val reseau = getReseau(typeLigne)

    // Si le réseau est correct
    if (reseau.isDefined) {
      // On vérifie que la ligne reçu existe dans ce réseau
      val testLigne = verifieLigne(nomLigne, typeLigne, reseau.get)
      // Si la ligne existe
      if(testLigne.isDefined) {
        val ligne = testLigne.get
        // On récupère les prochains départs de cette ligne
        val testDeparts = getDeparts(ligne)
        // Si on réussi à récupérer les départs de cette ligne
        if(testDeparts.isDefined) {
          val departs = testDeparts.get
          // On lance la surveillance de la ligne
          departs.arrivals.foreach(depart => println(depart.display_informations.headsign + " / " + depart.stop_point.name + " /\t\t" + convertDate(depart.stop_date_time.arrival_date_time)))
          val simpDate = new SimpleDateFormat("hh:mm");
          def now = (simpDate.format(Calendar.getInstance().getTime()))
          def arr = heureMax.dropRight(2).length() match {
            case 4 => "0"+heureMax.dropRight(2)
            case _ => heureMax.dropRight(2)
          }
          println(now,arr)
          while(now != arr) {
            Thread.sleep(20000)
            val res = getDeparts(ligne)
            if (res.isDefined) {
              val before = departs.arrivals
              val after = res.get.arrivals
              for (i <- 0 to res.get.arrivals.size-1) {
                // Pour les trains qui ont le même nom et qui ont la même gare d'arrivée
                if(before(i).display_informations.headsign == after(i).display_informations.headsign && before(i).stop_point.name == after(i).stop_point.name) {
                  /* On véirifie si les nouvelles données sont comparables au précédentes
                  ** Si le retard entre deux date est important (5 minutes ou plus)
                  ** alors on dit qu'il y a perturbatioon
                  */
                  val tempsLimite = ajouteMinutes(convertDate(before(i).stop_date_time.arrival_date_time), 5)
                  if(convertDate(after(i).stop_date_time.arrival_date_time).after(tempsLimite)) {
                    println("Pertubation détectée sur la ligne")
                    sender ! Perturbation(nomLigne)
                  }
                }
                val departs = res
              }
              res.get.arrivals.foreach(depart => println(depart.display_informations.headsign + " / " + depart.stop_point.name + " /\t\t" +  convertDate(depart.stop_date_time.arrival_date_time)))
            } else {
              println("Erreur du serveur, veuillez réessayer")
            }
          }
          println("Aucune pertubation détectée sur le temps imparti")
        } else {
          println("Impossible de récupérer les départs")
        }
      } else {
        println("Cette ligne n'existe pas")
      }
    } else {
      println("Impossible de récupérer ce réseau")
    }
    self ! akka.actor.PoisonPill

    def receive={
      case _ =>
    }
  }

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
              println(t)
              sender ! TrajetGoogle(t)
            }
            case 500 => println("Erreur du serveur, veuillez réessayer")
          }
        }
      }

  object Transport extends App {

    val system = ActorSystem("trajet")

    val transport = system.actorOf(Props(new Trajet("vert-galant+villepinte","17+place+des+reflets+courbevoie", Some(IdMdp("xxx","yyy")))))
    //transport ! Perturbation("b")
    //transport ! akka.actor.PoisonPill
  }
