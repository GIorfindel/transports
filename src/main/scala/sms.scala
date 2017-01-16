import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.actor.ActorSystem
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

import scala.concurrent.Future
import scala.util.{ Failure, Success }

object sms {
  def main(args: Array[String]) {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    //implicit val timeout = Timeout(5)
    val duration = Duration(5000, MILLISECONDS)

    val responseFuture: Future[HttpResponse] =
    Http().singleRequest(HttpRequest(uri = "https://smsapi.free-mobile.fr/sendmsg?user=xxx&pass=yyy&msg=blablabla"))
    val result = Await.result(responseFuture, duration).asInstanceOf[HttpResponse]
    result._1.intValue() match
    {
      case 200 => println("SMS envoyé")
      case 400 => println("Vous devez renseigner le login et le mot de passe")
      case 402 => println("Trop de SMS ont été envoyés, veuillez attendre")
      case 403 => println("Le service n'est pas activé ou le login/mot de passe est incorrect")
      case 500 => println("Erreur du serveur, veuillez réessayer")
    }
  }
}
