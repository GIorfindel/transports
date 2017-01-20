package tutorial.webapp

import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport
import org.scalajs.jquery._
import scala.scalajs.js





object TutorialApp extends JSApp {


	def afficheIti(iti:js.Dynamic): String = {
		if(iti.status.toString == "OK"){
			val r = iti.routes.asInstanceOf[js.Dictionary[js.Dynamic]]
			val route = r("0").asInstanceOf[js.Dictionary[js.Dynamic]]
			val legs = route("legs").asInstanceOf[js.Dictionary[js.Dynamic]]
			val leg = legs("0").asInstanceOf[js.Dictionary[js.Dynamic]]
			var s = "<p>Depart:" + leg("start_address") + ", Arrivee:" + leg("end_address") + "</p>"
			val steps = leg("steps").asInstanceOf[js.Dictionary[js.Dynamic]]
			for( k <- js.Object.keys(steps.asInstanceOf[js.Object])){
				val step = steps(k).asInstanceOf[js.Dictionary[js.Dynamic]]
				if(step("travel_mode").toString == "TRANSIT"){
					val t = step("transit_details")
					s = s + "<p>" + t.line.vehicle.name + ", line:"+ t.line.short_name + " vers " + t.headsign + "</p>"
					s = s +	"<p>--- arret " + t.departure_stop.name + ", a " + t.departure_time.text + "</p>"
					s = s +	"<p>--- sortir " + t.arrival_stop.name + ", a " + t.arrival_time.text + "</p>"
				}
			}
			s
		}else{
			"<p>Aucun resultat</p>"
		}
	}

	def setupUI(): Unit = {
		//jQuery("#rechItin").submit(rechItin _)
		jQuery.ajax(js.Dynamic.literal(
    			url = "http://localhost:8080/api",
    			success = { (data: js.Any, textStatus: String, jqXHR: JQueryXHR) =>
      				val json = js.JSON.parse(jqXHR.responseText)
							jQuery("#iti").html(afficheIti(json))
    			},
    			error = { (jqXHR: JQueryXHR, textStatus: String, errorThrow: String) =>
      				println(s"jqXHR=$jqXHR,text=$textStatus,err=$errorThrow")
    			},
    			`type` = "GET"
			).asInstanceOf[JQueryAjaxSettings])
	}

	def main(): Unit = {
		//appendPar("Hello World")
		jQuery(setupUI _)


	}
}
