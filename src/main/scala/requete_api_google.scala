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
import com.typesafe.sslconfig.akka.AkkaSSLConfig


case class Transit(geocoded_waypoints: List[Info],routes:List[Route],status:String)
case class Info(geocoder_status:String,partial_match:Boolean,place_id:String,types:List[String])
case class Route(bounds:Bound,copyrights:String,legs:List[Leg],status:String)
case class Bound(northeast:Coor,southwest:Coor)
case class Coor(lat:Double,lng:Double)
case class Leg(arrival_time:Time,departure_time:Time,distance:Distance,duration:Duration,end_address:String,end_location:Coor,start_address:String,start_location:Coor,steps:List[Step],overview_polyline:Polyline,summary:String,warnings:List[String])
case class Time(text:String,time_zone:String,value:Long)
case class Distance(text:String,value:Int)
case class Duration(text:String,value:Int)
case class Step(distance:Distance,duration:Duration,end_location:Coor,html_instructions:String,maneuver:String,polyline:Polyline,start_location:Coor,steps:List[Step2],travel_mode:String,transit_details:Transit_detail)
case class Step2(distance:Distance,duration:Duration,end_location:Coor,html_instructions:String,maneuver:String,polyline:Polyline,start_location:Coor,steps:List[Step3],travel_mode:String,transit_details:Transit_detail)
case class Step3(distance:Distance,duration:Duration,end_location:Coor,html_instructions:String,maneuver:String,polyline:Polyline,start_location:Coor,travel_mode:String,transit_details:Transit_detail)
case class Polyline(points:String)
case class Transit_detail(arrival_stop:Arrival_stop,arrival_time:Arrival_time,departure_stop:Departure_stop,departure_time:Departure_time,headsign:String,line:Line,num_stops:Int)
case class Arrival_stop(location:Coor,name:String)
case class Arrival_time(text:String,time_zone:String,value:Long)
case class Departure_stop(location:Coor,name:String)
case class Departure_time(text:String,time_zone:String,value:Long)
case class Line(agencies:List[Agencie],color:String,short_name:String,text_color:String,vehicle:Vehicle)
case class Agencie(name:String,url:String)
case class Vehicle(icon:String,name:String,typee:String)

object ClientJson extends DefaultJsonProtocol with SprayJsonSupport {
	implicit val vehicleF = jsonFormat3(Vehicle)
	implicit val agencieF = jsonFormat2(Agencie)
	implicit val coorF = jsonFormat2(Coor)
	implicit val durationF = jsonFormat2(Duration)
	implicit val distanceF = jsonFormat2(Distance)
	implicit val departure_timeF = jsonFormat3(Departure_time)
	implicit val departure_stopF = jsonFormat2(Departure_stop)
	implicit val arrival_timeF = jsonFormat3(Arrival_time)
	implicit val arrival_stopF = jsonFormat2(Arrival_stop)
	implicit val timeF = jsonFormat3(Time)
	implicit val polylineF = jsonFormat1(Polyline)
	implicit val boundF = jsonFormat2(Bound)
	implicit val lineF = jsonFormat5(Line)
	implicit val transit_detailF = jsonFormat7(Transit_detail)
	implicit val step3F = jsonFormat9(Step3)
	implicit val step2F = jsonFormat10(Step2)
	implicit val stepF = jsonFormat10(Step)
	implicit val legF = jsonFormat12(Leg)
	implicit val routeF = jsonFormat4(Route)
	implicit val infoF = jsonFormat4(Info)
	implicit val transitF = jsonFormat3(Transit)
}

object TestRR{
 
def main(args: Array[String]) {
	import ClientJson._
	implicit val system = ActorSystem("my-system")
	implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
	implicit val executionContext = system.dispatcher

	val connection = Http().outgoingConnection("maps.googleapis.com", 80)
	val request:HttpRequest = RequestBuilding.Get(s"/maps/api/directions/json?origin=26+rue+gaston,+daguenet,+france&destination=universite+paris+13+villetaneuse+france&mode=transit&key=KEY_A_REMPLIR")
	request.addHeader(HttpHeader("User-Agent",""))
	Source.single(request).via(connection).runWith(Sink.head).flatMap { response =>
		
	  response.status match {
	    case status if status.isSuccess =>{
	      println(response.entity)
	      val ticker = Unmarshal(response.entity).to[Transit]
	      println(ticker)
	      //val t = Await.result(ticker,10.second)
	      /*onSuccess(ticker) {
            	case transit => println(transit)
	      }*/
	      Future{5}
            }
	    case status =>{
	      println(s"$status error:${response.toString}")
	      Future{5}
	      }
	  }
	}
}
}
