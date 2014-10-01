// vim: ts=4:sw=4:et
(function() {
"use strict";
/* global _wt1Q:true, console:false */
/* jshint sub:true, eqnull:true */

var trackURL =  "//localhost:8080/wt1/public/p.gif";
var sessionLifetimeMinutes = 3;
var visitorLifeTimeMinutes = 2 * 365 * 24 * 60;

// Add Object.keys support for IE, without touching the global namespace
if (!Object.keys) {
  Object.wt1keys = (function () {
    var hasOwnProperty = Object.prototype.hasOwnProperty,
        hasDontEnumBug = !({toString: null}).propertyIsEnumerable('toString'),
        dontEnums = [
          'toString',
          'toLocaleString',
          'valueOf',
          'hasOwnProperty',
          'isPrototypeOf',
          'propertyIsEnumerable',
          'constructor'
        ],
        dontEnumsLength = dontEnums.length;
 
    return function (obj) {
      if ((typeof obj !== 'object' && typeof obj !== 'function') || obj === null) {
          throw new TypeError('Object.keys called on non-object');
      }
 
      var result = [];
 
      for (var prop in obj) {
        if (hasOwnProperty.call(obj, prop)) { result.push(prop); }
      }
 
      if (hasDontEnumBug) {
        for (var i=0; i < dontEnumsLength; i++) {
          if (hasOwnProperty.call(obj, dontEnums[i])) { result.push(dontEnums[i]); } 
        }
      }
      return result;
    };
  })();
} else {
  Object.wt1keys = function(o) { return Object.keys(o); };
}

/* Private constants */
var wt1spc = "__wt1spc", wt1vpc = "__wt1vpc";

function W1TTracker() {
    this.trackURL = trackURL;
    this.debugEnabled = false;
    this.sizeDetails = true;
}

W1TTracker.prototype.debug = function(x) {
    if (this.debugEnabled) { console.info(x); }
};

W1TTracker.prototype.isArray = function(a){
    if (a == null) { return false; }
    if(Array.isArray) {
        return Array.isArray(a);
    } else {
        return (typeof(a) === 'object' && a instanceof Array);
    }
};

W1TTracker.prototype.isObject = function(a) {
    if (a == null) { return false; }
    return typeof(a) === 'object';
};

/* Encode a query string from an object { a:1, b:"test 1" } --> "a=1&b=test+1" */
W1TTracker.prototype.encodeQS = function(params) {
    var urlParams = [];
    for (var p in params) {
        if (params.hasOwnProperty(p)) {
            urlParams.push(encodeURIComponent(p) + "=" + encodeURIComponent(params[p]));
        }
    }
    return urlParams.join("&").replace("%20", "+"); 
};

W1TTracker.prototype.decodeQS = function(qs) {
    if (qs === "") { return {}; }
    var b = {};
    var chunks = qs.split("&");
    for (var i = 0; i < chunks.length; ++i) {
        var subchunks= chunks[i].split('=');
        if (subchunks.length !== 2) { continue; }
        b[subchunks[0]] = decodeURIComponent(subchunks[1].replace(/\+/g, " "));
    }
    return b;
};

/* Encode a query string from an array of values: [a,b] --> "a=true&b=true" */
W1TTracker.prototype.encodeQSBooleans = function(params) {
    var urlParams = [];
    for (var p in params){
        if (params.hasOwnProperty(p)) {
            urlParams.push(encodeURIComponent(params[p]) + "=true");
        }
    }
    return urlParams.join("&").replace("%20", "+"); 
};

W1TTracker.prototype.getCookie = function(ckie) {
    var i, chunks;
    var cookies = document.cookie.split('; ');
    for (i = 0; i < cookies.length; i++) {
        chunks = cookies[i].split('=');
        if (chunks[0] === ckie) {
            return decodeURIComponent(chunks[1]);
        }
    }
    return null;
};

W1TTracker.prototype.setCookie = function(name, value, lifeMinutes){
    var ckie = name + '=' + encodeURIComponent(value) + "; ";
    var expr = new Date(new Date().getTime() + lifeMinutes * 60 * 1000);
    ckie += "expires=" + expr.toGMTString() + "; ";
    ckie += "path=/; ";
    ckie += "domain=" + document.location.host + "; ";
    document.cookie = ckie;
};

W1TTracker.prototype.getCookieAsMap = function(ckieName) {
    var ckie = this.getCookie(ckieName);
    if (ckie == null) {
        return {};
    } else {
        return this.decodeQS(ckie);
    }
};

W1TTracker.prototype.setCookieFromMap = function(name, map, lifeMinutes) {
    var qs = this.encodeQS(map);
    this.setCookie(name, qs, lifeMinutes);
};

W1TTracker.prototype.fillSizeParams = function(params) {
    var root= document.documentElement;

    params.__wt1bw = window.innerWidth || root.clientWidth || document.body.clientWidth;
    params.__wt1bh = window.innerHeight || root.clientHeight || document.body.clientHeight;
    params.__wt1sw = window.screen.width;
    params.__wt1sh = window.screen.height;
};

W1TTracker.prototype.push = function(command) {
    var params;
    if (!this.isArray(command)) {
        throw "Unexpected type as input of push :" + typeof(command); 
    }

    if (command[0] === "setSizeCapture") {
        this.sizeDetails = command[1] === "true" ? true : false;

    /* Visitor params */
    } else if (command[0] === "setVisitorParam") {
        params = this.getCookieAsMap(wt1vpc);
        params[command[1]] = command[2];
        this.setCookieFromMap(wt1vpc, params, visitorLifeTimeMinutes);
    } else if (command[0] === "delVisitorParam") {
        params = this.getCookieAsMap(wt1vpc);
        delete params[command[1]];
        this.setCookieFromMap(wt1vpc, params, visitorLifeTimeMinutes);
    } else if (command[0] === "clearVisitorParams") {
        this.setCookieFromMap(wt1vpc, {}, visitorLifeTimeMinutes);

    /* Session params */
    } else if (command[0] === "setSessionParam") {
        params = this.getCookieAsMap(wt1spc);
        params[command[1]] = command[2];
        this.setCookieFromMap(wt1spc, params, sessionLifetimeMinutes);
    } else if (command[0] === "delSessionParam") {
        params = this.getCookieAsMap(wt1spc);
        delete params[command[1]];
        this.setCookieFromMap(wt1spc, params, sessionLifetimeMinutes);
    } else if (command[0] === "clearSessionParams") {
         this.setCookieFromMap(wt1spc, {}, sessionLifetimeMinutes);

    } else if (command[0] === "trackPage") {
        if (command.length < 2 || command[1] == null) {
            // No param or null param is ok
            command[1] = [];
        }
        if (!this.isObject(command[1])) {
            throw "Unexpected parameter 1 to trackPage function, expected an object";
        }
        if (document.referrer != null){
            command[1].__wt1ref = document.referrer;
        }
        this.track("page", command[1]);
    } else if (command[0] === "trackEvent") {
        if (command.length < 2 || command[1] == null) {
            // No param or null param is ok
            command[1] = [];
        }
        if (!this.isObject(command[1])) {
            throw "Unexpected parameter 1 to trackEvent function, expected an object";
        }
        if (document.referrer != null){
            command[1].__wt1ref = document.referrer;
        }
        this.track("event", command[1]);
    } else {
        throw "Unexpected command in _wt1Q.push: " + command[0];
    }
};

W1TTracker.prototype.getVisitorId = function() {
    var visitorId = this.getCookie("__wt1vic");
    if (visitorId == null) {
        visitorId = Math.floor((1 + Math.random()) * 0x10000000).toString(16).substring(1) +
             Math.floor((1 + Math.random()) * 0x10000000).toString(16);
    }
    /* Always set the cookie to refresh its expiration time */
    this.setCookie("__wt1vic", visitorId, visitorLifeTimeMinutes);
    return visitorId;
};

W1TTracker.prototype.getSessionId = function() {
    var sessionId = this.getCookie("__wt1sic");
    if (sessionId == null) {
        sessionId = Math.floor((1 + Math.random()) * 0x10000000).toString(16).substring(1) +
             Math.floor((1 + Math.random()) * 0x10000000).toString(16);
    }
    /* Always set the cookie to refresh its expiration time */
    this.setCookie("__wt1sic", sessionId, sessionLifetimeMinutes);
    return sessionId;
};

W1TTracker.prototype.track = function(type, params) {
    this.debug("track ... " + type);
    params["__wt1ts"] = new Date().getTime();
    params["__wt1ty"] = type;
    params["__wt1tzo"] = new Date().getTimezoneOffset();
    params["__wt1lang"] = navigator.language || navigator.userLanguage;

    params["__wt1vic"] = this.getVisitorId();
    params["__wt1sic"] = this.getSessionId();
    if (this.getCookie(wt1vpc) != null) {
        params[wt1vpc] = this.getCookie(wt1vpc);
    }
    if (this.getCookie("__wt1spc") != null) {
        params[wt1spc] = this.getCookie(wt1spc);
    }

    if (this.sizeDetails) {
        this.fillSizeParams(params);
    }

    /* Encode the final query string */
    var url = this.encodeQS(params);
    var img = new Image(1, 1);
    img.src = this.trackURL + "?" + url;
};

/* Script is ready to load: retrieve the already pushed
 * functions, and execute them.
 * The _wt1Q array is replaced by the tracker object, which exposes the same push function
 */
if (typeof(_wt1Q) !== "undefined") {
    var prevCommands = _wt1Q;
    _wt1Q = new W1TTracker();
    _wt1Q.debug("Already had "+ prevCommands.length + " commands");
    for (var i = 0; i < prevCommands.length; i++) {
        _wt1Q.push(prevCommands[i]);
    }
} else {
    _wt1Q = new W1TTracker();
}

}());
