var mongoose = require('mongoose');

var sampleFrameSchema = mongoose.Schema({
    id: String,
    date: String,
    datasetId: String,
    timestamp: Number,
    endTimestamp: Number,
    sampleCount: Number,
    samples: Buffer
});
var SampleFrame = mongoose.model('SampleFrame', sampleFrameSchema);
module.exports = SampleFrame;

