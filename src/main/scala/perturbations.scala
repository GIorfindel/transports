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

  final case class Type(name: String)
  final case class Ligne(code: String, name: String, physical_modes: List[Type], text_color: String, color: String, id: String)
  final case class Reseau(lines: List[Ligne])

  implicit val typeFormat = jsonFormat1(Type)
  implicit val ligneFormat = jsonFormat6(Ligne)
  implicit val reseauFormat = jsonFormat1(Reseau)

  final case class Departures()

  def getType(str: String): String = str match {
    case "Bus" => "Bus"
    case "Train" => "RapidTransit"
    case "Tramway" => "Tramway"
    case "Metro" => "Metro"
  }

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

    // Token Navitia
    val authorization = headers.Authorization(BasicHttpCredentials("232e61e7-8b77-4a6b-8bc2-7b6dd2732d37", ""))

    // On recupere les transports du bon type
    val responseFuture: Future[HttpResponse] =
      Http().singleRequest(HttpRequest(
        uri = "https://api.navitia.io/v1/coverage/fr-idf/physical_modes/physical_mode:" + typeLigne + "/lines?count=1000&start_page=0",
        headers = List(authorization)))

    val result = Await.result(responseFuture, duration).asInstanceOf[HttpResponse]
    result._1.intValue() match {
      case 200 => {
        val ticker = Unmarshal(result.entity).to[Reseau]
        val res = Await.result(ticker, 10.second)

        // On regarde si le nom de la ligne reçu existe dans les donnees
        val testLigne = getLigne(nomLigne, res)
        if(testLigne.isDefined)  {
          // Si la ligne existe, on récupère sa ligne
          val ligne = testLigne.get
          println(ligne)
          // On récupère les infos sur la ligne
          /*val responseFuture: Future[HttpResponse] =
            Http().singleRequest(HttpRequest(
              uri = "https://api.navitia.io/v1/coverage/fr-idf/physical_modes/physical_mode:" + typeLigne + "/lines/"+ligne.id,
              headers = List(authorization)))*/

        }
        else {
          println("Cette ligne n'existe pas")
        }
      }
      case 500 => println("Erreur du serveur, veuillez réessayer")
    }
  }
}
