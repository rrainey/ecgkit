var mongoose = require('mongoose'),
    SampleFrame = require('./models/SampleFrame');

module.exports = {
    sync: function(req, res) {
        var i = 0;
        var done = false;
        var item;
        var result = [];
        for(i=0; !done; i++) {
            item = req.body[""+i];
            if (item !== null) {
                var samples = new Buffer(item.samples, "base64");
                var a = new SampleFrame({
                    id: item.id,
                    databaseId: item.databaseId,
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
            }
            else {
                done = true;
            }
        }

        res.json(result);
    }
}