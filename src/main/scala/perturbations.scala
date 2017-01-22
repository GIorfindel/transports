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
import akka.http.scaladsl.model.StatusCodes
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers._
import java.util.Calendar
import java.text.SimpleDateFormat

object Perturbations {

  // Constantes
  val NavitiaKey = "232e61e7-8b77-4a6b-8bc2-7b6dd2732d37"
  val duration = Duration(15000, MILLISECONDS)

  // Classes pour les requetes de lignes
  final case class Type(name: String)
  final case class Ligne(code: String, name: String, physical_modes: List[Type], text_color: String, color: String, id: String)
  final case class Reseau(lines: List[Ligne])

  implicit val typeFormat = jsonFormat1(Type)
  implicit val ligneFormat = jsonFormat6(Ligne)
  implicit val reseauFormat = jsonFormat1(Reseau)


  // Classes pour les requetes de departs
  final case class Display(direction: String, headsign: String)
  final case class StopPoint(name: String)
  final case class StopDate(arrival_date_time: String, departure_date_time: String, base_arrival_date_time: String, base_departure_date_time: String, data_freshness: String)
  final case class Informations(display_informations: Display, stop_point: StopPoint, stop_date_time: StopDate)
  final case class Departs(arrivals: List[Informations])

  implicit val stopDateFormat = jsonFormat5(StopDate)
  implicit val stopPointFormat = jsonFormat1(StopPoint)
  implicit val DisplayFormat = jsonFormat2(Display)
  implicit val informationsFormat = jsonFormat3(Informations)
  implicit val departuresFormat = jsonFormat1(Departs)

  // Nécessaire pour les requêtes
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  // Correspondance entre les type de ligne Google et Navitia
  def getType(str: String): String = str match {
    case "Bus" => "Bus"
    case "Train" => "RapidTransit"
    case "Commuter train" => "RapidTransit"
    case "Tram" => "Tramway"
    case "Subway" => "Metro"
  }

  // Récupérer la bonne ligne de transport dans une liste
  def getLigne(nomLigne: String, reseau: Reseau): Option[Ligne] = {
    (reseau.lines.find(ligne => ligne.code == nomLigne)) match {
      case Some(t) => Some(t)
      case _ => None
    }
  }

  // Requete pour récupérer les lignes du réseau selon son type (bus, train, etc.)
  def getReseau(typeLigne: String): Option[Reseau] = {
    val authorization = headers.Authorization(BasicHttpCredentials(NavitiaKey, ""))
    val uri = "https://api.navitia.io/v1/coverage/fr-idf/physical_modes/physical_mode:" + typeLigne + "/lines?count=1000"
    val requeteReseau: Future[HttpResponse] =
      Http().singleRequest(HttpRequest(
        uri = uri,
        headers = List(authorization)))
    val reponseReseau = Await.result(requeteReseau, duration).asInstanceOf[HttpResponse]
    reponseReseau._1.intValue() match {
      case 200 => {

        val ticker = Unmarshal(reponseReseau.entity).to[Reseau]
        val res = Await.result(ticker, 10.second)
        Some(res)
      }
      case 500 => None
    }
  }

  // Requete pour récupérer les départs
  def getDeparts(ligne: Ligne): Option[Departs] = {
    val authorization = headers.Authorization(BasicHttpCredentials(NavitiaKey, ""))
    val uri = "https://api.navitia.io/v1/coverage/fr-idf/lines/" + ligne.id + "/arrivals"
    val requeteReseau: Future[HttpResponse] =
      Http().singleRequest(HttpRequest(
        uri = uri,
        headers = List(authorization)))
    val reponseReseau = Await.result(requeteReseau, duration).asInstanceOf[HttpResponse]
    reponseReseau._1.intValue() match {
      case 200 => {
        val ticker = Unmarshal(reponseReseau.entity).to[Departs]
        val res = Await.result(ticker, 10.second)
        Some(res)
      }
      case 500 => None
    }
  }

  def convertDate(dateApi: String) = {
    val dateParts = Array(dateApi.substring(0, 4), dateApi.substring(4, 6), dateApi.substring(6, 8))
    val timeParts = Array(dateApi.substring(8, 11), dateApi.substring(11, 13), dateApi.substring(13, 15))
    val dateConv = dateParts.mkString("-") + timeParts.mkString(":") + "Z"
    val res = javax.xml.bind.DatatypeConverter.parseDateTime(dateConv).getTime()
    res
  }

  def ajouteMinutes(dateInit: java.util.Date, minute:Int) = {
    val tempsLimiteTemp = Calendar.getInstance()
    tempsLimiteTemp.setTime(dateInit)
    tempsLimiteTemp.add(Calendar.MINUTE, minute)
    tempsLimiteTemp.getTime()
  }

  // On vérifie les infos de la ligne
  def verifieLigne(nomLigne: String, typeLigne: String, res: Reseau):Option[Ligne] = {
    val testLigne = getLigne(nomLigne, res)
    if (testLigne.isDefined) {
      // Si la ligne existe, on récupère sa ligne
      Some(testLigne.get)
    }
    else {
      None
    }
  }

  // On récupère les départs de la ligne et on commence la surveillance
  def surveilleLigne(ligne: Ligne, departs: Departs, heureMax:String): Boolean = {
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
              return true
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
    return false
  }

  def main(args: Array[String]) {

    // Données reçu par Google
    val typeLigne = getType("Train")
    val nomLigne = "B"
    val heureMax = "02:20pm"

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
          surveilleLigne(ligne, departs, heureMax)
        } else {
          println("Impossible de récupérer les départs")
        }
      } else {
        println("Cette ligne n'existe pas")
      }
    } else {
      println("Impossible de récupérer ce réseau")
    }
  }
}
