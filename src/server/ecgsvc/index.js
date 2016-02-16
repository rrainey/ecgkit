var express = require('express'),
    bodyParser = require('body-parser'),
    https = require('https'),
    oauthserver = require('node-oauth2-server'),
    config = require('./config'),
    ecgapi = require('./ecgapi'),
    SampleFrame = require('./models/SampleFrame'),
    fs = require('fs'),
    mongoose = require('mongoose');

mongoose.connect(config.mongoConnection);
 
var app = express();

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

var options = {
    key: fs.readFileSync('./ssl/newkey.pem'),
    cert: fs.readFileSync('./ssl/certificate.pem')
};

var server = https.createServer(options, app).listen(config.port, function(){
  console.log("Express server listening on port " + config.port);
});
