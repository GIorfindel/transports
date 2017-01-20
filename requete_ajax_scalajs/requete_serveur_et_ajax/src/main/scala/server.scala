package server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.Done
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import akka.http.scaladsl.server.directives.ContentTypeResolver.Default
import akka.http.scaladsl.server.Route
import scala.io.StdIn
import scala.concurrent.Future
import akka.actor.Actor
import akka.http.scaladsl.client._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import java.util.concurrent.TimeUnit

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

object WebServer{

  def main(args: Array[String]) {
    import JsonFormatTransit._
    // needed to run the route
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    // needed for the future map/flatmap in the end
    implicit val executionContext = system.dispatcher

    val route =
		path("index") {
			 get {
				 getFromFile("index.html")
			 }
		 } ~
		 path("jsdep") {
 			 get {
 				 getFromFile("js/scala-js-tutorial-jsdeps.js")
 			 }
 		 } ~
		 path("fastop") {
 			 get {
 				 getFromFile("js/scala-js-tutorial-fastopt.js")
 			 }
 		 } ~
    path("api") {
        get {
					parameters('origine, 'destination) { (orig, dest) =>
	          val duration = Duration(15000, MILLISECONDS)
						val origin = orig.replace(' ', '+')
						val destination = dest.replace(' ', '+')
	      		//val origin = "gare+saint+lazarre+paris+france"
	      		//val destination = "universite+paris+13+villetaneuse+france"
	          val key = "TA_CLE"

	      		val responseFuture: Future[HttpResponse] =
	      		Http().singleRequest(HttpRequest(uri = s"https://maps.googleapis.com/maps/api/directions/json?origin=${origin}&destination=${destination}&mode=transit&key=${key}"))
	      		val result = Await.result(responseFuture, duration).asInstanceOf[HttpResponse]
	      		result._1.intValue() match
	      		{
	      			case 200 =>{
	      				val ticker = Unmarshal(result.entity).to[Transit]
	      				val t = Await.result(ticker,10.second)
	              complete(t)
	      			}
	      			case 500 =>{
	              println("Erreur du serveur, veuillez réessayer")
	              complete("erreur")
	            }
	      		}
					}
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ ⇒ system.terminate()) // and shutdown when done

  }
}
