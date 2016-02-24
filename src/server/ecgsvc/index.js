var express = require('express'),
    bodyParser = require('body-parser'),
    https = require('https'),
    oauthserver = require('oauth2-server'),
    config = require('./config.json')[process.env.NODE_ENV || 'dev'],
    ecgapi = require('./ecgapi'),
    SampleFrame = require('./models/SampleFrame'),
    fs = require('fs'),
    mongoose = require('mongoose'),
    nasync = require('async');

mongoose.connect(config.mongoConnection);
 
var app = express();

app.disable('x-powered-by');

app.use(bodyParser.urlencoded({ extended: true }));
 
app.use(bodyParser.json());
 
app.oauth = oauthserver({
    model: require('./models/model'),
    grants: ['password'],
    debug: true,
    accessTokenLifetime: 3600*24*365,
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
                        samples: samples,
                        user: mongoose.Types.ObjectId(req.user.id)
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

var options = {
    key: fs.readFileSync(config.sslKeyfile),
    cert: fs.readFileSync(config.sslCertfile)
};

var server = https.createServer(options, app).listen(config.port, function(){
  console.log("Express server listening on port " + config.port);
});
