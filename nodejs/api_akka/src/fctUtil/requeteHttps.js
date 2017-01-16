'use strict'

let request = require('request')
let pem = require('pem')


function requete(url, reponse, res) {
    pem.createCertificate({
        days: 1,
        selfSigned: true
    }, function(err, keys) {

        let options = {
            url: url,
            cert: keys.certificate,
            key: keys.serviceKey,
            passphrase: 'password'
        }
        request.get(options, function(error, response, body) {
            if (error) {
                console.log("erreur:" + error)
                res.statusCode = 400
                res.json({
                    info: "erreur"
                })
            } else if (response.statusCode == 200) {
                reponse(JSON.parse(body),res)
            } else {
                console.log("status:" + response.statusCode)
                res.statusCode = response.statusCode
                res.json({
                    info: "erreur"
                })
            }
        })

    })
}

exports.requete = requete
