# This list of commands is not currently intended to be executed as a script.
# It is more a notepad listing commands to be executed manually.

# Create collections and indices
db.createCollection("oauthclients")
db.sampleframes.createIndex( { "clientId": 1 }, { unique: true } )
db.createCollection("oauthusers")
db.sampleframes.createIndex( { "username": 1 }, { unique: true } )
db.createCollection("oauthaccesstokens")
db.createCollection("sampleframes")
db.createCollection("ecgsamples")
db.sampleframes.createIndex( { "id": 1 }, { unique: true } )
db.sampleframes.createIndex( { "datasetId": 1 }, { unique: false } )
db.ecgsamples.createIndex( { "datasetId": 1 }, { unique: true } )

# remove test records
db.sampleframes.remove({})
db.ecgsamples.remove({})

# remove all access tokens, active or otherwise
db.oauthaccesstokens.remove({})