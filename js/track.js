// vim: ts=4:sw=4:et

function W1TTracker() {
    this.trackURL =  "http://localhost:8080/wt1/public/p.gif";
    this.debugEnabled = false;
    this.setVisitorParams = {};
    this.delVisitorParams = [];
    this.clearVisitorParams = false;
    this.setSessionParams = {};
    this.delSessionParams = [];
    this.clearSessionParams = false;

    this.sizeDetails = true;
}

W1TTracker.prototype.debug = function(x) {
    if (this.debugEnabled) console.info(x);
}

W1TTracker.prototype.isArray = function(a){
    if (a == null) return false;
    if(Array.isArray) {
        return Array.isArray(a);   
    } else {
        return (typeof(a) == 'object' && a instanceof Array)        
    }
}
W1TTracker.prototype.isObject = function(a) {
    if (a == null) return false;
    return typeof(a) == 'object';
}

W1TTracker.prototype.fillSizeParams = function(params) {
    var root= document.documentElement;

    params.__wt1bw = window.innerWidth || root.clientWidth || document.body.clientWidth, 
    params.__wt1bh = window.innerHeight || root.clientHeight || document.body.clientHeight;
    params.__wt1sw = window.screen.width;
    params.__wt1sh = window.screen.height;
}

W1TTracker.prototype.push = function(command) {
    if (!this.isArray(command)) {
        throw "Unexpected type as input of push :" + typeof(command); 
    }
    
    if (command[0] == "setAccount") {
        this.account = command[1];

    } else if (command[0] == "setSizeCapture") {
        this.sizeDetails = command[1] == "true" ? true : false;
    /* Visitor params */
    } else if (command[0] == "setVisitorParam") {
        this.setVisitorParams[command[1]] = command[2];
    } else if (command[0] == "delVisitorParam") {
        this.delVisitorParams.push(command[1]);
    } else if (command[0] == "clearVisitorParams") {
        this.clearVisitorParam = true;
    /* Session params */
    } else if (command[0] == "setSessionParam") {
        this.setSessionParams[command[1]] = command[2];
    } else if (command[0] == "delSessionParam") {
        this.delSessionParams.push(command[1]);
    } else if (command[0] == "clearSessionParams") {
        this.clearSessionParam = true;
 
    } else if (command[0] == "trackPage") {
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
    } else if (command[0] == "trackEvent") {
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
}

/* Encode a query string from an object { a:1, b:"test 1" } --> "a=1&b=test+1" */
W1TTracker.prototype.encodeQS = function(params) {
    var urlParams = [];
    for (var p in params){
        urlParams.push(encodeURIComponent(p) + "=" + encodeURIComponent(params[p]))
    }
    return urlParams.join("&").replace("%20", "+"); 
}
/* Encode a query string from an array of values: [a,b] --> "a=true&b=true" */
W1TTracker.prototype.encodeQSBooleans = function(params) {
    var urlParams = [];
    for (var p in params){
        urlParams.push(encodeURIComponent(params[p]) + "=true");
    }
    return urlParams.join("&").replace("%20", "+"); 
}


W1TTracker.prototype.track = function(type, params) {
    this.debug("track ... " + type);
    params["__wt1ts"] = new Date().getTime();
    params["__wt1ty"] = type;
    params["__wt1tzo"] = new Date().getTimezoneOffset();
    params["__wt1lang"] = navigator.language || navigator.userLanguage;

    if (this.sizeDetails) {
        this.fillSizeParams(params);
    }

    /* If some params have been set/removed/cleared, send them in the request */
    if (Object.keys(this.setVisitorParams).length > 0) {
        params["__wt1vpsa"] = this.encodeQS(this.setVisitorParams);
        this.setVisitorParams = {};
    }
    if (this.delVisitorParams.length > 0) {
        params["__wt1vpda"] = this.encodeQSBooleans(this.delVisitorParams);
        this.delVisitorParams = [];
    }
    if (this.clearVisitorParams == true) {
        params["__wt1vpca"] = "true";
        this.clearVisitorParams = false;
    }
    if (Object.keys(this.setSessionParams).length > 0) {
        params["__wt1spsa"] = this.encodeQS(this.setSessionParams);
        this.setSessionParams = {};
    }
    if (this.delSessionParams.length > 0) {
        params["__wt1spda"] = this.encodeQSBooleans(this.delSessionParams);
        this.delSessionParams = [];
    }
    if (this.clearSessionParams == true) {
        params["__wt1spca"] = "true";
        this.clearSessionParams = false;
    }

    /* Encode the final query string */
    var url = this.encodeQS(params);
    var img = new Image(1, 1);
    img.src = this.trackURL + "?" + url;
}  

/* Script is ready to load: retrieve the already pushed
 * functions, and execute them.
 * The _wt1Q array is replaced by the tracker object, which exposes the same push functino
 */
if (typeof(_wt1Q) != "undefined") {
    var prevCommands = _wt1Q;
    _wt1Q = new W1TTracker();
    _wt1Q.debug("Already had "+ prevCommands.length + " commands");
    for (var i in prevCommands) {
        _wt1Q.push(prevCommands[i]);
    }
} else {
    _wt1Q = new W1TTracker();
}
