var mongoose = require('mongoose');


var ECGSampleSchema = mongoose.Schema({
    datasetId: String,
    date: String,                               // date collected (local time)
    timestamp: Number,                          // (ms)
    endTimestamp: Number,                       // (ms)
    sampleCount: Number,
    samples: Buffer,                            // array of 10-bit unsigned samples padded to 16-bits
    user: mongoose.Schema.Types.ObjectId,       // user in oauthusers
    created: { type: Date, default: Date.now }, // date last generated
    generationElapsedTime: Number               // (ms)
});
var ECGSample = mongoose.model('ECGSample', ECGSampleSchema);

module.exports = ECGSample;