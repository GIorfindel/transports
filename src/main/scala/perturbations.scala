import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import scala.io.StdIn
import scala.concurrent.Future
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import akka.Done
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers._

object Perturbations {

    // Classes pour les requetes de lignes
    final case class Type(name: String)
    final case class Ligne(code: String, name: String, physical_modes: List[Type], text_color: String, color: String, id: String)
    final case class Reseau(lines: List[Ligne])

    implicit val typeFormat = jsonFormat1(Type)
    implicit val ligneFormat = jsonFormat6(Ligne)
    implicit val reseauFormat = jsonFormat1(Reseau)


    // ICI
    // Classes pour les requetes de departs
    final case class Display(direction: String, headsign: String)
    final case class StopPoint(name: String)
    final case class StopDate(arrival_date_time: String, departure_date_time: String, base_arrival_date_time: String, base_departure_date_time: String, data_freshness: String)
    final case class Informations(display_informations: Display, stop_point: StopPoint, stop_date_time: StopDate)
    final case class Departs(departures: List[Informations])

    implicit val stopDateFormat = jsonFormat5(StopDate)
    implicit val stopPointFormat = jsonFormat1(StopPoint)
    implicit val DisplayFormat = jsonFormat2(Display)
    implicit val informationsFormat = jsonFormat3(Informations)
    implicit val departuresFormat = jsonFormat1(Departs)


  // Correspondance entre les type de ligne Google et Navitia
  def getType(str: String): String = str match {
    case "Bus" => "Bus"
    case "Train" => "RapidTransit"
    case "Tramway" => "Tramway"
    case "Metro" => "Metro"
  }

  // Récupérer la bonne ligne de transport dans une liste
  def getLigne(nomLigne: String, reseau: Reseau): Option[Ligne] = {
    (reseau.lines.find(ligne => ligne.code == nomLigne)) match {
      case Some(t) => Some(t)
      case _ => None
    }
  }


  def main(args: Array[String]) {

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val typeLigne = getType("Train")
    val nomLigne = "H"

    val duration = Duration(15000, MILLISECONDS)

    // Requete pour récupérer les lignes du réseau selon son type (bus, train, etc.)
    def getReseau(uri: String, key:String) = {
      val authorization = headers.Authorization(BasicHttpCredentials(key, ""))
      val requeteReseau: Future[HttpResponse] =
        Http().singleRequest(HttpRequest(
          uri = uri,
          headers = List(authorization)
        ))
      val reponseReseau = Await.result(requeteReseau, duration).asInstanceOf[HttpResponse]
      reponseReseau._1.intValue() match {
        case 200 => {

          val ticker = Unmarshal(reponseReseau.entity).to[Reseau]
          val res = Await.result(ticker, 10.second)
          verifieLigne(res)
        }
        case 500 => println("Erreur du serveur, veuillez réessayer")
      }
    }

    // Requete pour récupérer les départs
    def getDeparts(uri: String, key:String) = {
      val authorization = headers.Authorization(BasicHttpCredentials(key, ""))
      val requeteReseau: Future[HttpResponse] =
        Http().singleRequest(HttpRequest(
          uri = uri,
          headers = List(authorization)
        ))
      val reponseReseau = Await.result(requeteReseau, duration).asInstanceOf[HttpResponse]
      reponseReseau._1.intValue() match {
        case 200 => {
          val ticker = Unmarshal(reponseReseau.entity).to[Departs]
          val res = Await.result(ticker, 10.second)
          surveilleLigne(res)
        }
        case 500 => println("Erreur du serveur, veuillez réessayer")
      }
    }

    // On vérifie les infos de la ligne
    def verifieLigne(res:Reseau) = {
      val testLigne = getLigne(nomLigne, res)
      if (testLigne.isDefined) {
        // Si la ligne existe, on récupère sa ligne
        val ligne = testLigne.get
        println(ligne)
        // On récupère les infos sur la ligne
        val key = "232e61e7-8b77-4a6b-8bc2-7b6dd2732d37"
        val uri = "https://api.navitia.io/v1/coverage/fr-idf/physical_modes/physical_mode:" + typeLigne + "/lines/" + ligne.id + "/departures"
        getDeparts(uri, key)
      } else {
        println("Cette ligne n'existe pas")
      }
    }

    // On récupère les départs de la ligne et on commence la surveillance
    def surveilleLigne(departs:Departs) = {
      departs.departures.foreach(depart => println(depart))
    }

    // Token Navitia
    val key = "232e61e7-8b77-4a6b-8bc2-7b6dd2732d37"
    val uri1 = "https://api.navitia.io/v1/coverage/fr-idf/physical_modes/physical_mode:" + typeLigne + "/lines?count=1000"
    getReseau(uri1, key)


  }
}
