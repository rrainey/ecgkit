'use strict';

var expect = require('chai').expect,
    request = require('request');

var config = require('../config'),
    bodyParser = require('body-parser'),
    model = require('../models/model'),
    fs = require('fs'),
    mongoose = require('mongoose'),
    https = require('https'),
    oauthserver = require('node-oauth2-server'),
    SampleFrame = require('../models/SampleFrame'),
    express = require('express'),
    bcrypt = require('bcryptjs');

mongoose.connect(config.mongoConnection);

var salt = bcrypt.genSaltSync(10);

var tempRecords = [];
var assert = require('assert');
var auth_token;
var app = express();

app.use(bodyParser.urlencoded({ extended: true }));
 
app.use(bodyParser.json());

app.oauth = oauthserver({
  model: require('../models/model'),
  grants: ['password'],
  debug: true
});
 
app.all('/oauth/token', app.oauth.grant());

app.post('/api/sampleframe', app.oauth.authorise(), function(req, res) {
        var item;
        var result = [];
        req.body.data.forEach ( function(item) {
            var samples = new Buffer(item.samples, "base64");
            var a = new SampleFrame({
                id: item.id,
                datasetId: item.datasetId,
                date: item.date,
                timestamp: item.timestamp,
                endTimestamp: item.endTimestamp,
                sampleCount: item.sampleCount,
                samples: samples
            });
            a.save(function(err, a){
                if(err) return res.status(500).send('Error occurred: database error.');
                result.push({ id: a._id });
            });
        });

        res.json(result);
    });

app.get('/', app.oauth.authorise(), function (req, res) {
    res.send('Secret area');
});

app.use(app.oauth.errorHandler());

var deleteFolderRecursive = function (path) {
    if (fs.existsSync(path)) {
        fs.readdirSync(path).forEach(function (file, index) {
            var curPath = path + "/" + file;
            if (fs.lstatSync(curPath).isDirectory()) { // recurse
                deleteFolderRecursive(curPath);
            } else { // delete file
                fs.unlinkSync(curPath);
            }
        });
        fs.rmdirSync(path);
    }
};

suite('Server Tests', function () {

     describe('OAuth authentication', function () {
         
         before(function () {
    
        
        model.OAuthUsersModel.findOne({ username: 'bsmith' }, function(err, user) {
            if (user === null) { 
                console.log("user not present"); 
                var user = new model.OAuthUsersModel({
                    username: "bsmith",
                    password: bcrypt.hashSync('zxcv', salt),
                    firstname: 'Bill',
                    lastname: 'Smith',
                    email: 'bsmith@nowhere.com'
                    });
                user.save( function(err) { if (err) console.log("Error saving user: " + err) });
                               
            }
        });

        
        model.OAuthClientsModel.findOne({ clientId: "CLIENT_ID" }, function(err, client) {
            if (client === null) { 
                console.log("client_id already present");             
                var client = new model.OAuthClientsModel({
                    clientId: "CLIENT_ID",
                    clientSecret: null,
                    redirectUri: '/'
                    });
                client.save( function(err) { if (err) console.log("Error saving client: " + err) } ) }
        });
        
        var options = {
            key: fs.readFileSync('./ssl/newkey.pem'),
            cert: fs.readFileSync('./ssl/certificate.pem')
        };

        var server = https.createServer(options, app).listen(config.port, function(){
          console.log("Express server listening on port " + config.port);
        });

    });
               
    after(function () {
        //deleteFolderRecursive('./testca');
    });
        
         it('malformed request (client_id-only) should return 400', function (done) {
            var options = {
                url: 'https://DALM00543038A:9443/oauth/token',
                headers: {
                  'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: 'client_id=CLIENT_ID',
                rejectUnauthorized: false
            };
            request.post(options, function (err, res, body){
                expect(err).to.equal(null);
                //console.log("res " + JSON.stringify(res, null, 2));
                expect(res.statusCode).to.equal(400);
                done();
            });
         });
         
         it('malformed request (user-pwd-only) should return 400', function (done) {
            var options = {
                url: 'https://DALM00543038A:9443/oauth/token',
                headers: {
                  'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: 'username=bsmith&password=zxcv',
                rejectUnauthorized: false
            };
            request.post(options, function (err, res, body){
                expect(err).to.equal(null);
                //console.log("res " + JSON.stringify(res, null, 2));
                expect(res.statusCode).to.equal(400);
                done();
            });
         });
         
         it('malformed request (user-pwd-grant-only) should return 400', function (done) {
            var options = {
                url: 'https://DALM00543038A:9443/oauth/token',
                headers: {
                  'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: 'username=bsmith&password=zxcv&grant_type=password',
                rejectUnauthorized: false
            };
            request.post(options, function (err, res, body){
                expect(err).to.equal(null);
                //console.log("res " + JSON.stringify(res, null, 2));
                expect(res.statusCode).to.equal(400);
                done();
            });
         });
         
         it('invalid user should return 400', function (done) {
            var options = {
                url: 'https://DALM00543038A:9443/oauth/token',
                headers: {
                  'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: 'client_id=CLIENT_ID&grant_type=password&username=rileys&password=incorrect',
                rejectUnauthorized: false
            };
            request.post(options, function (err, res, body){
                expect(err).to.equal(null);
                //console.log("res " + JSON.stringify(res, null, 2));
                expect(res.statusCode).to.equal(400);
                done();
            });
        });
        
        it('valid credentials should return 200', function (done) {
            var options = {
                url: 'https://DALM00543038A:9443/oauth/token',
                headers: {
                  'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: 'client_id=CLIENT_ID&grant_type=password&username=bsmith&password=zxcv',
                rejectUnauthorized: false
            };
            request.post(options, function (err, res, body){
                expect(err).to.equal(null);
                expect(res.statusCode).to.equal(200);
                //console.log("err " + err);
                var b = JSON.parse(body);
                expect(b.token_type).to.equal('bearer');
                auth_token = b.access_token;
                expect(auth_token.length > 0);
                done();
            });
        });
         
         it('missing a bearer token; expect 400', function (done) {
            var options = {
                url: 'https://DALM00543038A:9443/',
                headers: {
                },
                rejectUnauthorized: false, 
            };
            request.get(options, function (err, res, body){
                expect(err).to.equal(null);
                //console.log("err " + err);
                //console.log("res " + JSON.stringify(res, null, 2));
                expect(res.statusCode).to.equal(400);
                done();
              });
        });
         
         it('use a bearer token', function (done) {
            var options = {
                url: 'https://DALM00543038A:9443/',
                headers: {
                  'Authorization': "Bearer " + auth_token
                },
                rejectUnauthorized: false, 
            };
            request.get(options, function (err, res, body){
                expect(err).to.equal(null);
                //console.log("err " + err);
                //expect(res.statusCode).to.equal(200);
                done();
              });
        });
        
        it('add a sample frame', function (done) {
            var options = {
                url: 'https://DALM00543038A:9443/api/sampleframe',
                headers: {
                  'Content-Type': 'application/json',
                  'Authorization': "Bearer " + auth_token
                },
                
                body: "{ \"data\": [ {\n" +
                  "\"id\": \"d62a0269-7aef-4458-9f99-363360c35be3.11111111\",\n" +
                  "\"date\": \"2015-01-01\",\n" +
                  "\"datasetId\": \"d62a0269-7aef-4458-9f99-363360c35be3\",\n" +
                  "\"timestamp\": 11111111,\n" +
                  "\"endTimestamp\": 11111111,\n" +
                  "\"sampleCount\": 20,\n" +
                  "\"samples\": \"MDEwMjAzMDQwNTA2MDcwODA5MTAxMTEyMTMxNDE1MTYxNzE4MTkyMA==\"\n" +
                "} ] }",
                
                
                rejectUnauthorized: false, 
            };
            request.post(options, function (err, res, body){
                expect(err).to.equal(null);
                //console.log("err " + err);
                //console.log("res " + JSON.stringify(res, null, 2));
                expect(res.statusCode).to.equal(200);
                done();
              });
        });
    });

});