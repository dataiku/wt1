//
//  WT1Tracker.m
//  Test3
//
//  Created by Clément Stenac on 07/12/12.
//  Copyright (c) 2012 Clément Stenac. All rights reserved.
//

#import "WT1Tracker.h"

@interface WT1Tracker ()  <NSURLConnectionDelegate>
/** List of unsent events in this session (with the current params) */
@property NSMutableArray* queue;
/** List of serialized previous sessions / session params to be sent */
@property NSMutableArray* unsentSessionsQueue;

/* Current data */
@property NSURLConnection* connection;
@property NSArray* objectsBeingSent;
@property NSData* dataBeingSent;
@property BOOL paramsChangedDuringSend;

@property NSDictionary* sessionParams;
@property NSDictionary* visitorParams;

@property NSString* folderName;

@property BOOL cancelSchedule;
@property BOOL scheduled;
@end


/** Internal representation of an event */
@interface WT1TrackerEvent : NSObject
@property NSString* type;
@property NSString* page;
@property NSMutableDictionary* params;
@end
@implementation WT1TrackerEvent
+ (WT1TrackerEvent*)create{
    return [[WT1TrackerEvent alloc] init];
}
- (id) init {
    self.params = [[NSMutableDictionary alloc] init];
    return self;
}
@end

@implementation WT1Tracker

+ (WT1Tracker*)trackerWithURL:(NSString *)url {
    WT1Tracker* ret = [[WT1Tracker alloc]init];
    ret.trackerURL = url;
    return ret;
}

- (id) init {
    self.sendInterval = 10;
    self.folderName = @"__WT1Queue";
    NSLog(@"Tracker starting, reloading queue from disk");
    self.queue = [[NSMutableArray alloc]init];
    self.unsentSessionsQueue = [[NSMutableArray alloc]init];
    [self loadQueueFromDisk];
    [self reschedule];
    return self;
}

#pragma mark Public API

- (void)afterParamsChange {
    /* When session or visitor params change, we need to flush the previous request */
    if ([self.queue count] > 0) {
        [self.unsentSessionsQueue addObject:[self makeRequest:self.queue]];
        [self.queue removeAllObjects];
    }
    
    /* If a request is progressing, note the fact that we must not requeue the events themselves, but the data
     for a new session */
    if (self.dataBeingSent) {
        self.paramsChangedDuringSend = YES;
    }
}

- (void) updateSessionParams:(NSDictionary*)sparams {
    [self afterParamsChange];
    self.sessionParams = [sparams copy];
}

- (void) updateVisitorParams:(NSDictionary*)vparams {
    [self afterParamsChange];
    self.visitorParams = [vparams copy];
}

- (void)enterBackground {
    NSLog(@"Entering background");
    if (self.connection != nil) {
        NSLog(@"Cancelling current connection and putting data back");
        [self.connection cancel];
        [self putDataBackInQueueAfterFailure];
        [self reschedule];
    }
    /* Serialize all current stuff */
    if ([self.queue count] > 0) {
        NSLog(@"Putting in queue the last stuff");
        [self.unsentSessionsQueue addObject:[self makeRequest:self.queue]];
        [self.queue removeAllObjects];
    }
    NSLog(@"Saving...");
    [self saveCurrentQueueToDisk];
}

- (void)enterForeground {
    NSLog(@"Resume from background - No need to reload - remove saved data");
    [self updateSessionParams:nil];
    NSString *rootPath = [NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) objectAtIndex:0];
    NSString *queuePath = [rootPath stringByAppendingPathComponent:self.folderName];
    
    NSFileManager* manager = [NSFileManager defaultManager];
    [manager removeItemAtPath:queuePath error:nil];  
}

- (void)trackPage:(NSString*)page {
    [self trackPage:page withParams:nil];
}

- (void)trackPage:(NSString*)page withParams:(NSDictionary*) params {
    WT1TrackerEvent* evt = [WT1TrackerEvent create];
    evt.page = page;
    evt.type = @"page";
    if (params != nil) {
        evt.params = [params copy];
    }
    [self.queue addObject:evt];
}

- (void)trackEvent:(NSString*)page withParams:(NSDictionary*) params {
    WT1TrackerEvent* evt = [WT1TrackerEvent create];
    evt.page = page;
    evt.type = @"event";
    if (params != nil) {
        evt.params = [params copy];
    }
    [self.queue addObject:evt];
}

- (void)sendNow {
    [self sendIfNeeded];
}


#pragma mark Serialization

/** Constructs the JSON object corresponding to a set of requests */
- (NSData*)makeRequest:(NSArray*)evts {
    NSMutableDictionary* dict = [[NSMutableDictionary alloc] init];
    
    NSString* vid;
    if ([[UIDevice currentDevice] respondsToSelector:@selector(identifierForVendor)])  {
        vid = [[[UIDevice currentDevice] identifierForVendor] UUIDString];
    } else {
        NSLog(@"Using IOS5");
        vid = [[UIDevice currentDevice] uniqueIdentifier];
    }
    [dict setObject:vid forKey:@"visitorId"];
    
    if (self.sessionParams != nil) {
        [dict setObject:self.sessionParams forKey:@"sparams"];
    }
    if (self.visitorParams != nil) {
        [dict setObject:self.visitorParams forKey:@"vparams"];
    }
    
    NSString* ua = [NSString stringWithFormat:@"iOS app '%@' %@",
                    [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleDisplayName"],
                    [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleVersion"]];
    [dict setObject:ua forKey:@"ua"];
    
    NSMutableArray* array = [[NSMutableArray alloc]init];
    for (WT1TrackerEvent* evt in evts) {
        NSMutableDictionary* innerDict = [[NSMutableDictionary alloc] init];
        [innerDict setObject:evt.type forKey:@"type"];
        if (evt.page != nil) {
            [innerDict setObject:evt.page forKey:@"page"];
        }
        [innerDict setObject:[WT1Tracker queryStringEncode:evt.params] forKey:@"params"];
        
        [array addObject:innerDict];
    }
    [dict setObject:array forKey:@"events"];
    
    return [NSJSONSerialization dataWithJSONObject:dict options:0 error: nil];
}


+(NSString*)queryStringEncode:(NSDictionary*)dict {
    NSMutableString* str = [NSMutableString string];
    int i = 0;
    for (id key in dict) {
        if (i++ > 0) [str appendString:@"&"];
        [str appendString:[key stringByAddingPercentEscapesUsingEncoding:NSUTF8StringEncoding]];
        [str appendString:@"="];
        [str appendString:[[dict objectForKey:key]stringByAddingPercentEscapesUsingEncoding:NSUTF8StringEncoding]];
    }
    return str;
}

#pragma mark Disk persistence handling

/** Clear the current disk queue and write the new contents */
- (void) saveCurrentQueueToDisk {
    NSError* error;
    
    NSString *rootPath = [NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) objectAtIndex:0];
    NSString *queuePath = [rootPath stringByAppendingPathComponent:self.folderName];
    
    NSFileManager* manager = [NSFileManager defaultManager];
    [manager removeItemAtPath:queuePath error:nil];
    [manager createDirectoryAtPath:queuePath withIntermediateDirectories:true attributes:nil error:&error];
    NSLog(@"Saving %i sessions", [self.unsentSessionsQueue count]);
    int i = 0;
    for (NSData* queuedSession in self.unsentSessionsQueue) {
        NSString* path = [queuePath stringByAppendingPathComponent:[NSString stringWithFormat:@"session_%i", i++]];
        NSLog(@"Writing file %@", path);
        if ([queuedSession writeToFile:path options:NSDataWritingAtomic error:&error]) {
            NSLog(@"OK");
        } else {
            NSLog(@"NOK, error is %@", error.description);
        }
    }
}

/** Loads the queue from disk */
- (void) loadQueueFromDisk {
    NSString *rootPath = [NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) objectAtIndex:0];
    NSString *queuePath = [rootPath stringByAppendingPathComponent:self.folderName];
    
    NSFileManager* manager = [NSFileManager defaultManager];
    
    for (NSString* path in [manager contentsOfDirectoryAtPath:queuePath error:nil]) {
        NSLog(@"Loading from %@", path);
        NSData* data = [manager contentsAtPath:[queuePath stringByAppendingPathComponent:path]];
        if (data != nil) {
            [self.unsentSessionsQueue addObject:data];
        }
        NSLog(@"Loaded it");
    }
    NSLog(@"Loaded %i sessions from disk", [self.unsentSessionsQueue count]);
    [manager removeItemAtPath:queuePath error:nil];
}

#pragma mark HTTP connection handling

- (void)connection:(NSURLConnection *)connection didFailWithError:(NSError *)error {
    NSLog(@"error %@", [error localizedDescription]);
    self.connection = nil;
    [self putDataBackInQueueAfterFailure];
    [self reschedule];
}

- (void)connection:(NSURLConnection *)connection didReceiveResponse:(NSURLResponse *)response {
    NSHTTPURLResponse* hresp = (NSHTTPURLResponse*)response;
    if (hresp.statusCode >= 200 && hresp.statusCode < 400) {
        NSLog(@"Connection OK %i", hresp.statusCode);
        self.dataBeingSent = nil;
        self.paramsChangedDuringSend = NO;
        self.objectsBeingSent = nil;
    } else {
        NSLog(@"COnnection NOK %i", hresp.statusCode);
        [self putDataBackInQueueAfterFailure];
    }
}

- (void)connectionDidFinishLoading:(NSURLConnection*)connection {
    self.connection = nil;
    [self reschedule];
}

- (void)doSendConnection {
    NSMutableURLRequest* req = [NSMutableURLRequest requestWithURL:
                                [NSURL URLWithString:self.trackerURL]];
    [req setHTTPMethod:@"POST"];
    [req setValue:@"application/json" forHTTPHeaderField:@"Content-Type"];
    [req setHTTPBody:self.dataBeingSent];
    
    self.connection =[[NSURLConnection alloc]initWithRequest:req delegate:self];
    if (self.connection) {
        NSLog(@"Connection OK");
    } else {
        NSLog(@"Connection NOK");
        [self reschedule];
    }
}


#pragma mark Global scheduling

- (void)reschedule {
    self.scheduled = YES;
    NSLog(@"scheduling ...");
    [self performSelector:@selector(onTimeout) withObject:self afterDelay:self.sendInterval];
}

- (void) putDataBackInQueueAfterFailure {
    /** If params changed during the send, OR if we were already sending a previous session, then
     don't re-enqueue the events, but the serialized session data */
    if (self.paramsChangedDuringSend || self.objectsBeingSent == nil) {
        [self.unsentSessionsQueue addObject:self.dataBeingSent];
    } else {
        [self.queue addObjectsFromArray:self.objectsBeingSent];
    }
    self.objectsBeingSent = nil;
    self.dataBeingSent = nil;
    self.paramsChangedDuringSend= NO; // Acknowledge;
}


- (void)onTimeout {
    NSLog(@"timeout, unscheduling");
    self.scheduled = NO;
    [self sendIfNeeded];
}

- (void)sendIfNeeded {
    /* First, if we have a late session, send it */
    if ([self.unsentSessionsQueue count] > 0) {
        NSLog(@"Sending previous session data");
        NSData* data = [self.unsentSessionsQueue lastObject];
        [self.unsentSessionsQueue removeLastObject];
        
        self.objectsBeingSent = nil;
        self.dataBeingSent = data;
        [self doSendConnection];
        return;
    }
    
    if ([self.queue count] > 0) {
        NSLog(@"Sending pending events");
        NSArray* array = [self.queue copy];
        [self.queue removeAllObjects];
        
        self.objectsBeingSent = array;
        self.dataBeingSent = [self makeRequest:array];
        [self doSendConnection];
        return;
    }
    
    NSLog(@"Nothing to do, sleeping");
    [self reschedule];
}

@end
