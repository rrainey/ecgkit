'use strict';

var expect = require('chai').expect,
    request = require('request');

var
    service = require('../service'),
    bodyParser = require('body-parser'),
    model = require('../models/model'),
    fs = require('fs'),
    mongoose = require('mongoose'),
    https = require('https'),
    oauthserver = require('node-oauth2-server'),
    SampleFrame = require('../models/SampleFrame'),
    express = require('express'),
    bcrypt = require('bcryptjs'),
    nasync = require('async');

var config = JSON.parse(fs.readFileSync('./config.json'))[process.env.NODE_ENV || 'dev'];

console.log(process.env.NODE_ENV);

console.log(JSON.stringify(config));

mongoose.connect(config.mongoConnection);

var salt = bcrypt.genSaltSync(10);

var tempRecords = [];
var assert = require('assert');
var auth_token;
var app = express();
var rooturl = 'https://' + config.testHost + ':9443/';
var tokenurl = 'https://' + config.testHost + ':9443/oauth/token';
var apiurl = 'https://' + config.testHost + ':9443/api/sampleframe';

app.use(bodyParser.urlencoded({ extended: true }));
 
app.use(bodyParser.json());

app.oauth = oauthserver({
  model: require('../models/model'),
  grants: ['password'],
  debug: true
});
 
app.all('/oauth/token', app.oauth.grant());


app.post('/api/sampleframe', app.oauth.authorise(), function(req, res) {
        var keys = [];
        var data = req.body.data;
        var toBeSaved = [];
        var calls = [];
        // 'data' array present?
        if ( typeof data === 'undefined' || data === null ) {
            res.json( { "keys": keys, "status": "missing data array in request" } );
            return;
        }
        nasync.each(data, function(item, callback) {
            var recordResponse = {};
            // all fields present?
            if (typeof item.id === 'string') {
                
                if (typeof item.datasetId === 'string' &&
                     typeof item.date === 'string' &&
                     typeof item.timestamp === 'number' &&
                     typeof item.sampleCount === 'number' &&
                     typeof item.samples === 'string') {
                    
                    var samples = new Buffer(item.samples, "base64");
                    
                    var a = new SampleFrame(
                        {
                            id: item.id,
                            datasetId: item.datasetId,
                            date: item.date,
                            timestamp: item.timestamp,
                            endTimestamp: item.endTimestamp,
                            sampleCount: item.sampleCount,
                            samples: samples
                        });
                    
                    SampleFrame.findOne( { "id": a.id }, function(err,f) {
                        if (f === null) {
                            a.save( function(err) {
                                if (err) {
                                    recordResponse = { "id": a.id, "status": "error" };
                                    console.error("error on save of sample frame: " + err);
                                }
                                else {
                                    recordResponse = { "id": a.id, "_id": a._id };
                                }
                                keys.push( recordResponse );
                                callback();
                            });
                        }
                        else {
                            recordResponse = { "id": a.id, "status": "duplicate" };
                            keys.push( recordResponse );   
                            callback();
                        }
                    });
                }
                else {
                    recordResponse = { "id": item.id, "status": "missing properties" };
                    keys.push( recordResponse );
                    callback();
                }
            }
            else {
                recordResponse = { "id": "", "status": "missing properties" };
                keys.push( recordResponse );
                callback();
            }
        },
        function(err) {
            // All tasks are done now
            res.json( { "keys": keys, "status": "ok" } );
        });
            
        
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
                key: fs.readFileSync(config.sslKeyfile),
                cert: fs.readFileSync(config.sslCertfile)
            };

            var server = https.createServer(options, app).listen(config.port, function(){
              console.log("Express server listening on port " + config.port);
            });

        });
               
        after(function () {
            // remove test records
            //SampleFrame.find({ datasetId: "06b5c78c-9836-466a-86fd-1342ceec5d4b"}).remove(function (err) {
            //  if (err) return console.error("could not remove samples at teardown");
            //});
        });
        
         it('malformed request (client_id-only) should return 400', function (done) {
            var options = {
                url: tokenurl,
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
                url: tokenurl,
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
                url: tokenurl,
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
                url: tokenurl,
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
                url: rooturl,
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
        
        it('add one sample frame', function (done) {
            var options = {
                url: apiurl,
                headers: {
                  'Content-Type': 'application/json',
                  'Authorization': "Bearer " + auth_token
                },
                
                body: "{ \"data\": [ {\n" +
                  "\"id\": \"06b5c78c-9836-466a-86fd-1342ceec5d4b.0\",\n" +
                  "\"date\": \"2015-01-01\",\n" +
                  "\"datasetId\": \"06b5c78c-9836-466a-86fd-1342ceec5d4b\",\n" +
                  "\"timestamp\": 0,\n" +
                  "\"endTimestamp\": 23,\n" +
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
         
         it('add multiple sample frames', function (done) {
            var options = {
                url: apiurl,
                headers: {
                  'Content-Type': 'application/json',
                  'Authorization': "Bearer " + auth_token
                },
                
                body: fs.readFileSync('./test/dataset.json'),
                rejectUnauthorized: false, 
            };
            request.post(options, function (err, res, body){
                expect(err).to.equal(null);
                //console.log("err " + err);
                //console.log("res " + body);
                var x = JSON.parse(body);
                expect(x.status).to.equal('ok');
                expect(x.keys[0].status).to.equal(undefined);
                expect(x.keys[0].id).to.equal("06b5c78c-9836-466a-86fd-1342ceec5d4b.10021");
                expect(res.statusCode).to.equal(200);
                done();
              });
        });
         
         it('attempt to add duplicate sample frames', function (done) {
            var options = {
                url: apiurl,
                headers: {
                  'Content-Type': 'application/json',
                  'Authorization': "Bearer " + auth_token
                },
                
                body: fs.readFileSync('./test/dataset.json'),
                rejectUnauthorized: false, 
            };
            request.post(options, function (err, res, body){
                expect(err).to.equal(null);
                //console.log("err " + err);
                //console.log("res " + JSON.stringify(body, null, 2));
                var x = JSON.parse(body);
                expect(x.keys[0].status).to.equal('duplicate');
                expect(x.keys[0].id).to.equal("06b5c78c-9836-466a-86fd-1342ceec5d4b.10021");
                expect(res.statusCode).to.equal(200);
                done();
              });
        });
         
         it('attempt to add single batched sample frame', function (done) {
            var options = {
                url: apiurl,
                headers: {
                  'Content-Type': 'application/json',
                  'Authorization': "Bearer " + auth_token
                },
                
                body: fs.readFileSync('./test/dataset2.json'),
                rejectUnauthorized: false, 
            };
            request.post(options, function (err, res, body){
                expect(err).to.equal(null);
                //console.log("err " + err);
                //console.log("res " + JSON.stringify(body, null, 2));
                var x = JSON.parse(body);
                expect(x.status).to.equal('ok');
                expect(x.keys[0].status).to.equal(undefined);
                expect(x.keys[0].id).to.equal("da38eb30-402b-4fe7-b3b5-79d6c99848cf.10685");
                expect(res.statusCode).to.equal(200);
                done();
              });
        });
    });

});