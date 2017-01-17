import akka.actor.Actor
import akka.stream.scaladsl.Sink
import akka.http.scaladsl.client._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshalling.Marshal
import akka.stream.scaladsl.Source
import akka.stream.ActorMaterializer
import akka.stream.ActorMaterializerSettings
import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }
import akka.util.ByteString
import java.util.concurrent.CompletionStage
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import akka.http.scaladsl.Http
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import scala.util.{ Failure, Success }

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


object gooleApi {
	def main(args: Array[String]) {
		import JsonFormatTransit._
		implicit val system = ActorSystem()
		implicit val materializer = ActorMaterializer()
		implicit val executionContext = system.dispatcher
		val duration = Duration(15000, MILLISECONDS)

		val origin = "gare+saint+lazarre+paris+france"
		val destination = "universite+paris+13+villetaneuse+france"

		val responseFuture: Future[HttpResponse] =
		Http().singleRequest(HttpRequest(uri = s"https://maps.googleapis.com/maps/api/directions/json?origin=${origin}&destination=${destination}&mode=transit&key=TA_CLE"))
		val result = Await.result(responseFuture, duration).asInstanceOf[HttpResponse]
		result._1.intValue() match
		{
			case 200 =>{
				println("SMS envoyé")
				val ticker = Unmarshal(result.entity).to[Transit]
				val t = Await.result(ticker,10.second)
				println(t)
			}
			case 500 => println("Erreur du serveur, veuillez réessayer")
		}
  	}
}
