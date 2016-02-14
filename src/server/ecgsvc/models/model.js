/**
 * Copyright 2013-present NightWorld.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var mongoose = require('mongoose'),
    Schema = mongoose.Schema,
    bcrypt = require('bcryptjs'),
    model = module.exports;

var salt = bcrypt.genSaltSync(10);

//
// Schema definitions
//
var OAuthAccessTokensSchema = new Schema({
  accessToken: { type: String },
  clientId: { type: String },
  userId: { type: String },
  expires: { type: Date }
});

var OAuthRefreshTokensSchema = new Schema({
  refreshToken: { type: String },
  clientId: { type: String },
  userId: { type: String },
  expires: { type: Date }
});

var OAuthClientsSchema = new Schema({
  clientId: { type: String },
  clientSecret: { type: String },
  redirectUri: { type: String }
});

var OAuthUsersSchema = new Schema({
  username: { type: String },
  password: { type: String },       // stored as a bcryptjs hash
  firstname: { type: String },
  lastname: { type: String },
  email: { type: String, default: '' }
});

mongoose.model('OAuthAccessTokens', OAuthAccessTokensSchema);
mongoose.model('OAuthRefreshTokens', OAuthRefreshTokensSchema);
mongoose.model('OAuthClients', OAuthClientsSchema);
mongoose.model('OAuthUsers', OAuthUsersSchema);

var OAuthAccessTokensModel = mongoose.model('OAuthAccessTokens'),
  OAuthRefreshTokensModel = mongoose.model('OAuthRefreshTokens'),
  OAuthClientsModel = mongoose.model('OAuthClients'),
  OAuthUsersModel = mongoose.model('OAuthUsers');

model.OAuthUsersModel = OAuthUsersModel;
model.OAuthClientsModel = OAuthClientsModel;

//
// oauth2-server callbacks
//
model.getAccessToken = function (bearerToken, callback) {
  console.log('in getAccessToken (bearerToken: ' + bearerToken + ')');

  OAuthAccessTokensModel.findOne({ accessToken: bearerToken }, callback);
};

model.getClient = function (clientId, clientSecret, callback) {
  console.log('in getClient (clientId: ' + clientId + ', clientSecret: ' + clientSecret + ')');
  if (clientSecret === null || typeof clientSecret === undefined) {
    OAuthClientsModel.findOne({ clientId: clientId }, callback);
  }
  OAuthClientsModel.findOne({ clientId: clientId, clientSecret: clientSecret }, callback);
};

// This will very much depend on your setup, I wouldn't advise doing anything exactly like this but
// it gives an example of how to use the method to restrict certain grant types
var authorizedClientIds = ['CLIENT_ID', 'CLIENT_ID2'];
model.grantTypeAllowed = function (clientId, grantType, callback) {
  var result = false;
  console.log('in grantTypeAllowed (clientId: ' + clientId + ', grantType: ' + grantType + ')');

    // password grant type always allowed in our case
  if (grantType === 'password') {
      //return true;
      result = true;
    //return callback(false, authorizedClientIds.indexOf(clientId) >= 0);
  }

  callback(false, result);
};

model.saveAccessToken = function (token, clientId, expires, userId, callback) {
  console.log('in saveAccessToken (token: ' + token + ', clientId: ' + clientId + ', userId: ' + userId + ', expires: ' + expires + ')');
    
    if (typeof userId === undefined || userId === null) {
        console.log("no valid access token to save; probably invalid credentials");
    } else {
      var accessToken = new OAuthAccessTokensModel({
        accessToken: token,
        clientId: clientId,
        userId: userId,
        expires: expires
      });

      accessToken.save(callback);
    }
};

/*
 * Required to support password grant type
 */
model.getUser = function (username, password, callback) {
  var result = false;
  console.log('in getUser (username: ' + username +')');

  OAuthUsersModel.findOne({ username: username }, function(err, user) {
      if(err) return callback(err);
      // user exists?
      if (user) {
          // password match?
          if (bcrypt.compareSync(password, user.password)) {
              result = user._id;
          }
      }
      
      callback(null, result);
  });
};

/*
 * Required to support refreshToken grant type
 */
model.saveRefreshToken = function (token, clientId, expires, userId, callback) {
  console.log('in saveRefreshToken (token: ' + token + ', clientId: ' + clientId +', userId: ' + userId + ', expires: ' + expires + ')');

  var refreshToken = new OAuthRefreshTokensModel({
    refreshToken: token,
    clientId: clientId,
    userId: userId,
    expires: expires
  });

  refreshToken.save(callback);
};

model.getRefreshToken = function (refreshToken, callback) {
  console.log('in getRefreshToken (refreshToken: ' + refreshToken + ')');

  OAuthRefreshTokensModel.findOne({ refreshToken: refreshToken }, callback);
};