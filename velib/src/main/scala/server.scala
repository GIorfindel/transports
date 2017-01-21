import akka.actor.Actor
import akka.http.scaladsl.client._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.actor.ActorSystem
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.Http
import java.util.concurrent.TimeUnit


case class Fields(dist: String, available_bikes:Int, address:String)
case class Velibs(fields:Fields)
case class Transit(records:List[Velibs])

//pour api google(distance)
case class DistanceEnMetre(value : Int)
case class Distance(distance : DistanceEnMetre)
case class Legs(legs : List[Distance])
case class Routes(routes:List[Legs])

object JsonFormatTransit extends DefaultJsonProtocol with SprayJsonSupport {

	
	implicit val FieldsF = jsonFormat3(Fields)
	implicit val VelibsF = jsonFormat1(Velibs)
	implicit val TransitF = jsonFormat1(Transit)
	
}

object JsonFormatDistance extends DefaultJsonProtocol with SprayJsonSupport {

	
	implicit val DistanceEnMetreF = jsonFormat1(DistanceEnMetre)
	implicit val DistanceF = jsonFormat1(Distance)
	implicit val LegsF = jsonFormat1(Legs)
	implicit val RoutesF = jsonFormat1(Routes)
	
}


object gooleApi {
	def main(args: Array[String]) {
		
		
		implicit val system = ActorSystem()
		implicit val materializer = ActorMaterializer()
		implicit val executionContext = system.dispatcher
		val duration = Duration(15000, MILLISECONDS) 

		val xa = "48.8763"
		val ya = "2.32388"

		val xb = "48.8718"
		val yb = "2.3399"
//requete pour calculer la distance entre a et b
import JsonFormatDistance._
val responseFutureDistance: Future[HttpResponse] =
		Http().singleRequest(HttpRequest(uri = s"https://maps.googleapis.com/maps/api/directions/json?origin="+xa+"%2C"+ya+"&destination="+xb+"%2C"+yb+"&mode=bicycling&key=TA CLE"))
		val resultDistance = Await.result(responseFutureDistance, duration).asInstanceOf[HttpResponse]
		resultDistance._1.intValue() match
		{
			case 200 =>{
				val ticker = Unmarshal(resultDistance.entity).to[Routes]
				val t = Await.result(ticker,10.second)
				val distanceDepAr = t.routes(0).legs(0).distance.value
				if (distanceDepAr > 5000)
					println("Tros loin car la distance entre point depart et arrivee est :" + distanceDepAr + "> 5000 !!!")
				else
					println("La distance entre point depart et arrivee est : " + distanceDepAr)

			}	
				
		}

//requete pour trouver velib le plus proche de a(point depart)
		val responseFutureDepart: Future[HttpResponse] =
		Http().singleRequest(HttpRequest(uri = s"http://opendata.paris.fr/api/records/1.0/search/?dataset=stations-velib-disponibilites-en-temps-reel&geofilter.distance="+xa+"%2C"+ya+"%2C500"))
		val resultDepart = Await.result(responseFutureDepart, duration).asInstanceOf[HttpResponse]
		resultDepart._1.intValue() match
		{
			case 200 =>{
				import JsonFormatTransit._
				val ticker = Unmarshal(resultDepart.entity).to[Transit]
				val t = Await.result(ticker,10.second)
				//println(t)
				val s =t.records match {
					case List()=> "Pas de velib trouvé"
					case x::xs => "Tu peux prendre ton velib à cette addresse : " + x.fields.address + " le nombre vélo disponible est : " + x.fields.available_bikes.toString +
										" et sa distance avec A : " + x.fields.dist 

				}
				println(s)
			}
			case 500 => println("Erreur du serveur, veuillez réessayer")
		}


//requete pour trouver velib le plus proche de b(point d'arrivee)

		val responseFutureArrivee: Future[HttpResponse] =
		Http().singleRequest(HttpRequest(uri = s"http://opendata.paris.fr/api/records/1.0/search/?dataset=stations-velib-disponibilites-en-temps-reel&geofilter.distance="+xb+"%2C"+yb+"%2C500"))
		val resultArrivee = Await.result(responseFutureArrivee, duration).asInstanceOf[HttpResponse]
		resultArrivee._1.intValue() match
		{
			case 200 =>{
				import JsonFormatTransit._
				val ticker = Unmarshal(resultArrivee.entity).to[Transit]
				val t = Await.result(ticker,10.second)
				//println(t)
				val s =t.records match {
					case List()=> "Pas de velib trouvé"
					case x::xs => "Tu peux rendre ton velib à cette adresse : " + x.fields.address  +
										" et " + x.fields.dist + "c'est la distance entre ta destination et le station velib le plus proche "

				}
				println(s)
			}
			case 500 => println("Erreur du serveur, veuillez réessayer")
		}
  	}
}
