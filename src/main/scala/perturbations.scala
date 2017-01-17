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

object Perturbations {

  final case class Meta(version: String, date: String, call: String)
  final case class Metro(line: String, slug: String, title: String, message: String)
  final case class Rer(line: String, slug: String, title: String, message: String)
  final case class Tramway(line: String, slug: String, title: String, message: String)
  final case class Response(metros: List[Metro], rers: List[Rer], tramways: List[Tramway])
  final case class Traffic(response: Response, _meta: Meta)

  implicit val metaFormat = jsonFormat3(Meta)
  implicit val metroFormat = jsonFormat4(Metro)
  implicit val rerFormat = jsonFormat4(Rer)
  implicit val tramwayFormat = jsonFormat4(Tramway)
  implicit val responseFormat = jsonFormat3(Response)
  implicit val trafficFormat = jsonFormat2(Traffic)

  def main(args: Array[String]) {

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val duration = Duration(15000, MILLISECONDS)

    val responseFuture: Future[HttpResponse] =
      Http().singleRequest(HttpRequest(uri = "https://api-ratp.pierre-grimaud.fr/v2/traffic/"))
    val result = Await.result(responseFuture, duration).asInstanceOf[HttpResponse]
    result._1.intValue() match {
      case 200 => {
        println(result.entity)
        val ticker = Unmarshal(result.entity).to[Traffic]
        val t = Await.result(ticker, 10.second)
        println(t)
      }
      case 500 => println("Erreur du serveur, veuillez réessayer")
    }
    //complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, responseFuture.toString()))

  }
}
