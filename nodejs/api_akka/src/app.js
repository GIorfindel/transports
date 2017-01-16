'use strict'
const express = require('express')
const bodyParser = require('body-parser')
const reqHttps = require('./fctUtil/requeteHttps')
const gApi = require('./fctUtil/googleApi')
const request_logger = require('morgan')


let app = express()

app.use(request_logger('dev'))
app.use(bodyParser.json())
app.use(bodyParser.urlencoded({extended: false}))


app.get('/googleApi', (req, res) => {
    reqHttps.requete(gApi.creeUrl(req.query.origine,req.query.destination), gApi.filtre, res)
})

app.listen(8081)

/*
curl -v -X GET "http://localhost:8081/googleApi?origine=gare+saint+lazarre,+france&destination=universite+paris+13+villetaneuse+france"
*/
