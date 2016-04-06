var express = require('express'),
    exphbs  = require('express-handlebars'),
    session = require('express-session'),
    bodyParser = require('body-parser'),
    https = require('https'),
    oauthserver = require('oauth2-server'),
    config = require('./config.json')[process.env.NODE_ENV || 'dev'],
    //ecgapi = require('./ecgapi'),
    SampleFrame = require('./models/SampleFrame'),
    ECGSample = require('./models/ECGSample'),
    fs = require('fs'),
    mongoose = require('mongoose'),
    nasync = require('async');


mongoose.connect(config.mongoConnection);

function restrict(req, res, next) {
    if (req.session.user) {
        next();
    } 
    else {
        res.redirect('/login');
    }
}
 
var app = express();

MongoStore = require('connect-mongo/es5')(session);

app.use(session({
    secret: 'ecg',
    store: new MongoStore( {
        url: config.mongoConnection,
        autoRemove: 'interval',
        autoRemoveInterval: 120     // minutes
    }
    ),
    resave: true,
    saveUninitialized: true
}));

app.engine('handlebars', exphbs({defaultLayout: 'main'}));
app.set('view engine', 'handlebars');

app.disable('x-powered-by');

app.use(express.static('public'));

app.use(bodyParser.urlencoded({ extended: true }));
 
app.use(bodyParser.json());
 
app.oauth = oauthserver({
    model: require('./models/model'),
    grants: ['password'],
    debug: false,
    accessTokenLifetime: 3600*24*365,
    //continueAfterResponse: true
});
 
app.all('/oauth/token', app.oauth.grant());

app.get('/login', function (req, res) {
    res.render('login', {session: req.session});
});

app.get('/ecg', restrict, function (req, res) {
    res.render('ecg', {session: req.session});
});

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
                
                // normalize id by zero-padding timestamp portion
                var tempId = item.id.split('.');
                var s = "0000000" + tempId[1];
                var paddedId = tempId[0] + '.' + s.substr(s.length-8);

                // add sample frame
                var a = new SampleFrame(
                    {
                        id: paddedId,
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

app.get('/api/sampleframe', app.oauth.authorise(), function(req, res) {
    
    var datasetId = req.query.datasetId;
    
    if (datasetId !== null && typeof datasetId !== "undefined") {
        SampleFrame.find({'datasetId': datasetId }).
            sort({'timestamp': 1}).
            exec(function(err, frames) {
                if (err)
                    res.send({"error": err});
                else if (frames.length > 0) {
                    res.send({ "data": frames });
                }
                else {
                    res.send({ "data": [] });
                }
            });
    }
    else {
        res.send( {"error": "missing a datasetId parameter"});
    }
});

app.get('/api/samples', app.oauth.authorise(), function(req, res) {
    // return ids and date of each unique data set
    var data = [];
    var status = "ok";
    SampleFrame.find({}).
        distinct( "datasetId" , function(err, frames) {
            if (err)
                res.send({"error": err});
            else {
                //res.send(frames);
                nasync.each(frames, function(item, callback) {
                    SampleFrame.find({"datasetId": item}, function(err, fs) {
                        // we should get a list back -- data fields we're interest in here 
                        // will be the same for each row
                        if (fs.length > 0) {
                            data.push( {"datasetId": fs[0].datasetId, "date": fs[0].date});
                        }
                        else {
                            status = "error";
                        }
                        callback();
                    });
                },
                function(err) {
                    // All tasks are done now
                    res.json( { "data": data, "status": status } );
                });
            }
    });
});
        
app.get('/api/samples/:id', app.oauth.authorise(), function(req, res) {
    var response = {};
    ECGSample.findOne({datasetId: req.params.id}, function(err, sample) {
        if (err)
                res.send(err);
        if (sample) {
            // cached consolidated version of the data exists.
            res.send(sample);
        }
        else {
            // No consolidated sample cached for this ECG dataaset -- generate it.
            if (typeof req.params.id === 'undefined') {
                res.send({error: "must supply a record id"});
            }
            var start = new Date().getTime();
            SampleFrame.find({datasetId: req.params.id}).
                sort({ id: 1 }).
                exec(function(err, frames) {
                    var date;
                    var datasetId;
                    var user;
                    var timestamp = 0;
                    var endTimestamp = 0;
                    var sampleCount = 0;
                    var firstFrame = true;
                    var cur = 0;
                
                    if (err)
                        res.send( {error: err} );
                
                    frames.forEach(function(frame) {
                        sampleCount += frame.sampleCount;
                    });
                    var buf = new Buffer(sampleCount*2);
                    frames.forEach(function(frame) {
                        var j = 0;
                        if (firstFrame) {
                            firstFrame = false;
                            datasetId = frame.datasetId;
                            date = frame.datasetId;
                            user = frame.user;
                            timestamp = frame.timestamp;
                        }
                        endTimestamp = frame.endTimestamp;
                        // copy buffer
                        frame.samples.copy(buf, cur);
                        cur += frame.sampleCount*2;
                    });
                
                    var end = new Date().getTime();
                    var elapsed = end - start;
                
                    var a = new ECGSample(
                    {
                        datasetId: datasetId,
                        date: date,
                        timestamp: timestamp,
                        endTimestamp: endTimestamp,
                        sampleCount: sampleCount,
                        samples: buf,
                        user: user,
                        generationElapsedTime: elapsed
                    });
                
                    a.save( function(err) {
                        if (err) {
                            res.send({error: "error on save of ECG sample: " + err.errmsg });
                        }
                        else {
                            // mask metadata values and send result
                            delete a["_id"];
                            delete a["user"];
                            delete a["generationElapsedTime"];
                            res.send(a);
                        }
                    });
                });
        }
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
