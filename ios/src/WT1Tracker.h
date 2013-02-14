//
//  WT1Tracker.h
//  Test3
//
//  Created by Clément Stenac on 07/12/12.
//  Copyright (c) 2012 Clément Stenac. All rights reserved.
//

/**
 * An events tracker
 */
@interface WT1Tracker : NSObject

/** Factory method: create a tracker targeting the given API URL */
+ (WT1Tracker*)trackerWithURL:(NSString*)url;

/** Sets the dictionary of session parameters */
- (void)updateSessionParams:(NSDictionary*)dictionary;
/** Sets the dictionary of visitor parameters */
- (void)updateVisitorParams:(NSDictionary*)dictionary;

/** Tracks a page view */
- (void)trackPage:(NSString*)page;
/** Tracks a page view, with additional params */
- (void)trackPage:(NSString*)page withParams:(NSDictionary*)params;
/** 
 * Tracks a custom event on a page, with additional params
 * @param page : optional, can be nil
 */
- (void)trackEvent:(NSString*)page withParams:(NSDictionary*)params;

/** Force immediate send of queued tracking events */
- (void)sendNow;

/** Call this when the application enters background */
- (void)enterBackground;
/** Call this when the application resumes from background */
- (void)enterForeground;

/** Target tracker API URL */
@property NSString* trackerURL;
/** Interval in seconds between sends of the queued tracking events */
@property int sendInterval;

@end
