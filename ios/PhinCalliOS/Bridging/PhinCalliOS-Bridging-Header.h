// Objective-C bridging header exposing Avaya WebRTC Connect SDK 4.0.10 to Swift.
// Guide 4.0.10 (p. 45): SDK supports Swift 5.1+ interoperability through this header.

#ifndef PhinCalliOS_Bridging_Header_h
#define PhinCalliOS_Bridging_Header_h

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

#if __has_include(<OceanaCustomerWebVoiceVideo/OceanaCustomerWebVoiceVideo.h>)
#import <OceanaCustomerWebVoiceVideo/OceanaCustomerWebVoiceVideo.h>
#endif

#endif /* PhinCalliOS_Bridging_Header_h */
