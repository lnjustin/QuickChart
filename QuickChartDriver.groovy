/**
 *  ****************  Quick Chart Driver  ****************
 *
 *  Design Usage:
 *  This driver works with the Quick Chart app.
 *
 *  Copyright 2022 Bryan Turcotte (@bptworld)
 *  
 *  This App is free.  If you like and use this app, please be sure to mention it on the Hubitat forums!  Thanks.
 *
 *  Remember...I am not a professional programmer, everything I do takes a lot of time and research (then MORE research)!
 *  Donations are never necessary but always appreciated.  Donations to support development efforts are accepted via: 
 *
 *  Paypal at: https://paypal.me/bptworld
 * 
 *  Unless noted in the code, ALL code contained within this app is mine. You are free to change, ripout, copy, modify or
 *  otherwise use the code in anyway you want. This is a hobby, I'm more than happy to share what I have learned and help
 *  the community grow. Have FUN with it!
 * 
 * ------------------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  If modifying this project, please keep the above header intact and add your comments/credits below - Thank you! -  @BPTWorld
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  Changes:
 *
 *  All changes are listed in the child app
 */

metadata {
	definition (name: "Quick Chart Driver", namespace: "BPTWorld", author: "Bryan Turcotte", importUrl: "") {
        capability "Actuator"
        
        //command "iframeHandler", ["string"]
        
        attribute "chart", "string"
        attribute "chartLength", "number"
        attribute "ifChart", "string"
	}
	preferences() {    	
        section(){
            input name: "about", type: "paragraph", element: "paragraph", title: "<b>Quick Chart</b>", description: "This device was created by Quick Chart<br>"
        }
    }
}

def iframeHandler(sendToQC) {
    theFrame = "<iframe src=\"${sendToQC}\" style=\"width:100vw;height:100vh;border:1px solid black;\"></iframe>"
    sendEvent(name: "ifChart", value: theFrame, isStateChange: true)
}