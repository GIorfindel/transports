import akka.actor.Actor
import akka.http.scaladsl._
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
import akka.Done

case class Ligne(nom:String,vehicule:String)
case class Transit(arret_depart:String,arret_arrive:String,temps_depart:String,temps_arrive:String,terminus:String,nb_arret:Int,ligne:Ligne)
case class Route(distance:String,duree:String,instruction:String,transit:Transit)
case class Itineraire(routes:List[Route])


object ClientJson extends DefaultJsonProtocol with SprayJsonSupport {
	implicit val ligneF = jsonFormat2(Ligne)
	implicit val transitF = jsonFormat7(Transit)
	implicit val routeF = jsonFormat4(Route)
	implicit val itineraireF = jsonFormat1(Itineraire)
}

object TestRR{
 
def main(args: Array[String]) {
	import ClientJson._
	implicit val system = ActorSystem("my-system")
	implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
	implicit val executionContext = system.dispatcher

	val connection = Http().outgoingConnection("localhost", 8081)
	val request:HttpRequest = RequestBuilding.Get(s"/googleApi?origine=gare+saint+lazarre,+france&destination=universite+paris+13+villetaneuse+france")
	//    I want to add header here
	//    request.addHeader(HttpHeader("User-Agent","Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)"))
	Source.single(request).via(connection).runWith(Sink.head).flatMap { response =>
		
	  response.status match {
	    case status if status.isSuccess =>{
	      val itinFuture = Unmarshal(response.entity).to[Itineraire]
	      //val t = Await.result(itinFuture,10.second)
	      //println(t)
	      Future{akka.Done}
            }
	    case status =>{
	      println(s"$status error:${response.toString}")
	      Future{akka.Done}
	      }
	  }
	}
}
}
