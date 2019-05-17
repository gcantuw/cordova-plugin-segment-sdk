#import "SegmentPlugin.h"
#import <Cordova/CDV.h>
#import <Analytics/SEGAnalytics.h>
#import <AdSupport/ASIdentifierManager.h> 

@implementation SegmentPlugin : CDVPlugin

- (void)pluginInitialize
{
    NSLog(@"[cordova-plugin-segment-sdk] plugin initialized");

    // Advertising ID output
    // NSLog(@"Advertising ID: %@", [[[ASIdentifierManager sharedManager] advertisingIdentifier] UUIDString]);

    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(finishLaunching:) name:UIApplicationDidFinishLaunchingNotification object:nil];
}

- (void)finishLaunching:(NSNotification *)notification
{
    NSString* writeKeyPreferenceName;
    NSString* writeKeyPListName;

    //Get app credentials from config.xml or the info.plist if they can't be found
    writeKeyPreferenceName = @"ios_segment_write_key";
    writeKeyPListName = @"AnalyticsWriteKey";
    NSString* writeKey = self.commandDelegate.settings[writeKeyPreferenceName] ?: [[NSBundle mainBundle] objectForInfoDictionaryKey:writeKeyPListName];

    if (writeKey.length) {
        
        NSString* useLocationServices = self.commandDelegate.settings[@"analytics_use_location_services"] ?: [[NSBundle mainBundle] objectForInfoDictionaryKey:@"AnalyticsUserLocationServices"];

        // Segment iOS SDK configuration options
        SEGAnalyticsConfiguration *configuration = [SEGAnalyticsConfiguration configurationWithWriteKey:writeKey];

        configuration.shouldUseLocationServices = [useLocationServices boolValue];
        configuration.trackApplicationLifecycleEvents = NO;
        configuration.flushAt = 1;
        
        [SEGAnalytics setupWithConfiguration:configuration];

    } else {
        NSLog(@"[cordova-plugin-segment-sdk] ERROR - Invalid Segment write key");
    }
}

- (void)identify:(CDVInvokedUrlCommand*)command
{
    NSString* userId = [command.arguments objectAtIndex:0];
    NSDictionary* traits = [command.arguments objectAtIndex:1];
    NSDictionary* options = [command.arguments objectAtIndex:2];

    if (traits == (id)[NSNull null]) {
        traits = nil;
    }

    [[SEGAnalytics sharedAnalytics] identify:userId traits:traits options:options];
}

- (void)group:(CDVInvokedUrlCommand*)command
{
    NSString* groupId = [command.arguments objectAtIndex:0];
    NSDictionary* traits = [command.arguments objectAtIndex:1];

    if (traits == (id)[NSNull null]) {
        traits = nil;
    }

    [[SEGAnalytics sharedAnalytics] group:groupId traits:traits];
}

- (void)track:(CDVInvokedUrlCommand*)command
{
    NSString* event = [command.arguments objectAtIndex:0];
    NSDictionary* properties = [command.arguments objectAtIndex:1];
    NSDictionary* options = [command.arguments objectAtIndex:2];

    if (properties == (id)[NSNull null]) {
        properties = nil;
    }

    [[SEGAnalytics sharedAnalytics] track:event properties:properties options:options];
}

- (void)screen:(CDVInvokedUrlCommand*)command
{
    NSString* name = [command.arguments objectAtIndex:0];
    NSDictionary* properties = [command.arguments objectAtIndex:1];
    NSDictionary* options = [command.arguments objectAtIndex:2];

    if (properties == (id)[NSNull null]) {
        properties = nil;
    }

    [[SEGAnalytics sharedAnalytics] screen:name properties:properties options:options];

}

- (void)alias:(CDVInvokedUrlCommand*)command
{
    NSString* newId = [command.arguments objectAtIndex:0];

    [[SEGAnalytics sharedAnalytics] alias:newId];
}

- (void)reset:(CDVInvokedUrlCommand*)command
{
    [[SEGAnalytics sharedAnalytics] reset];
}

- (void)flush:(CDVInvokedUrlCommand*)command
{
    [[SEGAnalytics sharedAnalytics] flush];
}

- (void)enable:(CDVInvokedUrlCommand*)command
{
    [[SEGAnalytics sharedAnalytics] enable];
}

- (void)disable:(CDVInvokedUrlCommand*)command
{
    [[SEGAnalytics sharedAnalytics] disable];
}

- (void)getAnonymousId:(CDVInvokedUrlCommand*)command
{
    NSString *anonymousId = [[SEGAnalytics sharedAnalytics] getAnonymousId];
    NSMutableDictionary *dic = [[NSMutableDictionary alloc] init];
    [dic setValue: anonymousId forKey: @"anonymousId"];
    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary: dic];
    [self.commandDelegate sendPluginResult: result callbackId: command.callbackId];
}

@end
