'use strict'
function filtre(itineraire,res){
  if(itineraire.status != 'OK'){
    console.log("status:"+itineraire.status)
    res.statusCode = 404
    res.json({
        info: "pas trouve"
    })
    return
  }

  let s = itineraire.routes[0].legs[0].steps
  let reponse = {routes:[]}
  let c = 0
  for(let x in s){
    let etape = s[x]
    if(etape.travel_mode == 'TRANSIT'){
      reponse.routes[c] = {}
      let r = reponse.routes[c]
      c += 1
      r.distance = etape.distance.text
      r.duree = etape.duration.text
      r.instruction = etape.html_instructions
      r.transit = {}
      r.transit.arret_depart = etape.transit_details.departure_stop.name
      r.transit.arret_arrive = etape.transit_details.arrival_stop.name
      r.transit.temps_depart = etape.transit_details.departure_time.text
      r.transit.temps_arrive = etape.transit_details.arrival_time.text
      r.transit.terminus = etape.transit_details.headsign
      r.transit.nb_arret = etape.transit_details.num_stops
      r.transit.ligne ={}
      r.transit.ligne.nom = etape.transit_details.line.short_name
      r.transit.ligne.vehicule = etape.transit_details.line.vehicle.type
    }
  }
  res.statusCode = 200
  res.json(reponse)
}

let urlGoogle = 'https://maps.googleapis.com/maps/api/directions/json?'
let queryGoogleFin = '&mode=transit&key=AIzaSyBQnr9uPGyQtYnIk_kbD_MmzV9hYfyqOXM'

function creeUrl(origine,destination){
  return urlGoogle + 'origin=' + origine + '&destination=' + destination + queryGoogleFin
}

exports.filtre = filtre
exports.creeUrl = creeUrl
