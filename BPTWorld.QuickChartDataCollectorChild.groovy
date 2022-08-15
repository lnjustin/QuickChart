/**
 *  **************** Quick Chart Data Collector Child App  ****************
 *
 *  Design Usage:
 *  Data colletor for use with Quick Chart
 *
 *  Copyright 2022 Bryan Turcotte (@bptworld)
 * 
 *  This App is free.  If you like and use this app, please be sure to mention it on the Hubitat forums!  Thanks.
 *
 *  Remember...I am not a professional programmer, everything I do takes a lot of time and research!
 *  Donations are never necessary but always appreciated.  Donations to support development efforts are accepted via: 
 *
 *  Paypal at: https://paypal.me/bptworld
 * 
 *  Unless noted in the code, ALL code contained within this app is mine. You are free to change, ripout, copy, modify or
 *  otherwise use the code in anyway you want. This is a hobby, I'm more than happy to share what I have learned and help
 *  the community grow. Have FUN with it!
 * 
 *-------------------------------------------------------------------------------------------------------------------
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
 *  App and Driver updates can be found at https://github.com/bptworld/Hubitat/
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  Changes:
 *
 *  Changes are listed in the Child app
 */

#include BPTWorld.bpt-normalStuff

def setVersion(){
    state.name = "Quick Chart Data Collector"
}

definition(
    name: "Quick Chart Data Collector Child",
    namespace: "BPTWorld",
    author: "Bryan Turcotte",
    description: "Data colletor for use with Quick Chart",
    category: "Convenience",
	parent: "BPTWorld:Quick Chart",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
	importUrl: "",
)

preferences {
    page(name: "pageConfig")
    page name: "dataStoragePage", title: "", install: false, uninstall: false, nextPage: "pageConfig"
}

def pageConfig() {
    dynamicPage(name: "", title: "", install: true, uninstall: true) {
		display() 
        section("${getImage('instructions')} <b>Instructions:</b>", hideable: true, hidden: true) {
			paragraph "<b>Notes:</b>"
    		paragraph "Data colletor for use with Quick Chart"
		}
        
        section(getFormat("header-green", "${getImage("Blank")}"+" App Control")) {
            appControlSection()
        }
        
        section(getFormat("header-green", "${getImage("Blank")}"+" Data Storage")) {
            if(fName) {
                href "dataStoragePage", title: " Data Storage", description: " Storing data in: ${fName}"
            } else {
                href "dataStoragePage", title: " Data Storage", description: " Click here to setup Data Storage."
            }
        }
        
        section(getFormat("header-green", "${getImage("Blank")}"+" Data Type")) {
            input "dataType", "enum", title: "Select the type of data to store", options: [
                ["rawdata":"Raw Data Over Time - Temp,Humidity,etc."],
                ["duration":"How long things have been on/open/active/etc per xx"]
            ], submitOnChange:true
        }
        
        section(getFormat("header-green", "${getImage("Blank")}"+" Device Options")) {
            if(dataType == "rawdata") {
                input "theDevices", "capability.*", title: "Select Device", multiple:true, submitOnChange:true
                if(theDevices) {
                    allAttrs = []
                    attTypes = [:]
                    theDevices.each { dev ->
                        attributes = dev.supportedAttributes
                        attributes.each { att ->
                            allAttrs << att.name
                            attTypes[att.name] = att.getDataType()
                        }
                    }
                    devAtt = allAttrs.unique().sort()                
                    input "theAttr", "enum", title: "Select the Attributes<small>All attributes must be the same type (string,number,etc.).</small>", options: devAtt, multiple:true, submitOnChange:true
                    
                    def areTypesEqual = true
                    def attType = null
                    theAttr.each { att ->                        
                        theType = attTypes[att]
                        if (attType == null) attType = theType
                        if (theType != attType) areTypesEqual = false
                    }                    
                    if (!areTypesEqual) paragraph "*Selected attributes are not all of the same type*"
                    state.isNumericalData = attType.toLowerCase() == "number" ? true : false
                }
            } else if(dataType == "duration") {
                input "theDevices", "capability.*", title: "Select Device", multiple:true, submitOnChange:true
                if(theDevices) {
                    allAttrs = []
                    theDevices.each { dev ->
                        attributes = dev.supportedAttributes
                        attributes.each { att ->
                            allAttrs << att.name
                        }
                    }
                    devAtt = allAttrs.unique().sort()                
                    input "theAttr", "enum", title: "Select the Attributes<br><small>All devices must share this same attribute.</small>", options: devAtt, multiple:false, submitOnChange:true
                    state.isNumericalData = true
                }
            }
        }
        
        if(dataType == "duration") {
            section(getFormat("header-green", "${getImage("Blank")}"+" Duration Options")) {
                attributesHandler(theAttr)
                input "aStatus", "enum", title: "Select a status to track", options: statusOptions, submitOnChange:true
                input "updateTime", "enum", title: "When to Update", options: [
                    ["statusChange":"Status Change"],
                    ["5min":"Every 5 Minutes"],
                    ["10min":"Every 10 Minutes"],
                    ["15min":"Every 15 Minutes"],
                    ["30min":"Every 30 Minutes"],
                    ["1hour":"Every 1 Hour"],
                    ["3hour":"Every 3 Hours"]
                ], defaultValue:"statusChange", submitOnChange:true
                paragraph "<small>* Max storage is 7 days</small>"
                paragraph state.message
            }
        }
        
        if(dataType == "rawdata") {
            section(getFormat("header-green", "${getImage("Blank")}"+" Collection Options")) {
                // Data Point                         
                input "updateTime", "enum", title: "When to Update", options: [
                    ["manual":"Manual"],
                    ["realTime":"Real Time"],
                    ["5min":"Every 5 Minutes"],
                    ["10min":"Every 10 Minutes"],
                    ["15min":"Every 15 Minutes"],
                    ["30min":"Every 30 Minutes"],
                    ["1hour":"Every 1 Hour"],
                    ["3hour":"Every 3 Hours"]
                ], defaultValue:"manual", submitOnChange:true

                input "recordAll", "bool", title: "Record data even if it's the same as previous value", defaultValue:false, submitOnChange:true    
                if (theAttr) {
                    if (state.isNumericalData && !recordAll) {
                        input "diffPerc", "bool", title: "Use value difference (off) OR percentage difference (on)", defaultValue:false, submitOnChange:true
                        if(diffPerc) {
                            paragraph "* Using Percentage Difference"
                        } else {
                            paragraph "* Using Value Difference"
                        }
                        input "diff", "number", title: "Difference from previous value to record a data point (range: 0..100)", range: '0..100', defaultValue:0, submitOnChange:true
                    }
                }
                input "dataPoints", "number", title: "How many data point to keep per option", submitOnChange:true, required: true
                if(updateTime && dataPoints) {
                    if(updateTime == "manual" || updateTime == "realTime") {
                        if(updateTime == "manual") {
                            input "getManual", "bool", title: "Get Data Now", submitOnChange:true
                            if(getManual) {
                                getDataHandler()
                                app?.updateSetting("getManual",[value:"false",type:"bool"])
                            }
                        }
                    } else {
                        min = updateTime.findAll( /\d+/ )*.toInteger()
                        min = min.toString().replace("[","").replace("]","")
                        hours = (min.toInteger() * dataPoints) / 60

                        paragraph "This will save ${hours} hours of data"
                        int actualPoints = dataPoints * (theDevices.size() * theAttr.size())
                        paragraph "Based on options selected: ${dataPoints} Datapoints x (${theDevices.size()} Device(s) x ${theAttr.size()} Attribute(s)) will be $actualPoints points of data saved."
                    }
                }
            }
        }
        
        section(getFormat("header-green", "${getImage("Blank")}"+" General")) {
            appGeneralSection()
        }
        
        if(logEnable) {
            section() {
                input "clearMap", "bool", title: "Clear the Maps and Empty the File (can NOT be undone)", submitOnChange:true
                if(clearMap) {
                    state.deviceStartMap = [:]
                    state.eventList = []
                    if(fName) {
                        writeFile(fName, state.eventList)
                    }
                    app?.updateSetting("clearMap",[value:"false",type:"bool"])
                }
                input "lastMap", "bool", title: "Clear lastMap (can NOT be undone)", submitOnChange:true
                if(lastMap) {
                    state.lastMap = [:]
                    app?.updateSetting("lastMap",[value:"false",type:"bool"])
                }
            }
        }
		display2()
	}
}

def dataStoragePage() {
    dynamicPage(name: "dataStoragePage", title: "", install:false, uninstall:false) {
		display() 
        section(getFormat("header-green", "${getImage("Blank")}"+" Data File")) {
            paragraph "<b>Hub Security</b><br>In order to read/write files you must specify your Hubitat admin username and password, if enabled."
            input "hubSecurity", "bool", title: "Hub Security", submitOnChange: true
            if(hubSecurity) {
                input "hubUsername", "string", title: "Hub Security username", submitOnChange:true
                input "hubPassword", "password", title: "Hub Security password", submitOnChange:true
            } else {
                app.removeSetting("hubUsername")
                app.removeSetting("hubPassword")
            }
            paragraph "<hr>"
            paragraph "<b>Data File</b>"            
            input "fName", "text", title: "Name of file to Use <small>(ie. humidityData.txt)</small><br><small>* File must end in .txt</small>", submitOnChange:true
            input "showFiles", "bool", title: "Show Files List", defaultValue:false, submitOnChange:true
            if(showFiles) {
                paragraph "<hr>"
                filesToShow = "<b>Files List</b><br>"
                if(!state.fileList) getFileList()
                theFiles = state.fileList.sort()
                theFiles.each { ftol ->
                    filesToShow += "$ftol<br>"
                }
                paragraph "$filesToShow"            
                input "updateFiles", "bool", title: "Update Files List", defaultValue:false, submitOnChange:true
                if(updateFiles) {
                    getFileList()
                    app?.updateSetting("updateFiles",[value:"false",type:"bool"])
                }
                paragraph "<hr>"
            }
            
            if(fName) {
                if(!state.fileList) getFileList()
                fileExists()
                if(fileExists) {
                    paragraph "File already exists."
                } else {
                    paragraph "File will be created."
                }
                paragraph "Press 'Next' to complete"
            } else {
                paragraph ""
            }
            paragraph "Note: If you want to delete the file at any time. <a href='http://${location.hub.localIP}:8080/hub/fileManager' target=_blank>Click Here</a> to visit the File Manager."
        }
        display2()
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {	
    if(logEnable) log.debug "Updated with settings: ${settings}"
	unschedule()
    unsubscribe()
    if(logEnable && logOffTime == "1 Hour") runIn(3600, logsOff, [overwrite:false])
    if(logEnable && logOffTime == "2 Hours") runIn(7200, logsOff, [overwrite:false])
    if(logEnable && logOffTime == "3 Hours") runIn(10800, logsOff, [overwrite:false])
    if(logEnable && logOffTime == "4 Hours") runIn(14400, logsOff, [overwrite:false])
    if(logEnable && logOffTime == "5 Hours") runIn(18000, logsOff, [overwrite:false])
    if(logEnagle && logOffTime == "Keep On") unschedule(logsOff)
	initialize()
}

def initialize() {
    checkEnableHandler()
    if(pauseApp) {
        log.info "${app.label} is Paused"
    } else {
        if(dataType == "rawdata") {
            if(updateTime == "realTime") {
                if(theDevices && theAttr) {
                    theDevices.each { td ->
                        theAttr.each { ta ->
                            subscribe(td, ta, getDataHandler)
                        }
                    }
                }
            } else if(updateTime == "5min") {
                runEvery5Minutes(getDataHandler)
            } else if(updateTime == "10min") {
                runEvery10Minutes(getDataHandler) 
            } else if(updateTime == "15min") {
                runEvery15Minutes(getDataHandler)
            } else if(updateTime == "30min") {
                runEvery30Minutes(getDataHandler)
            } else if(updateTime == "1hour") {
                runEvery1Hour(getDataHandler)
            } else if(updateTime == "3hour") {
                runEvery3Hours(getDataHandler)
            }
        }
        
        if(dataType == "duration") {
            if(theAttr) {
                theDevices.each { td ->
                    subscribe(td, theAttr, durationHandler)
                }
            }            
            if(updateTime == "5min") {
                runEvery5Minutes(durationUpdater)
            } else if(updateTime == "10min") {
                runEvery10Minutes(durationUpdater) 
            } else if(updateTime == "15min") {
                runEvery15Minutes(durationUpdater) 
            } else if(updateTime == "30min") {
                runEvery30Minutes(durationUpdater) 
            } else if(updateTime == "1hour") {
                runEvery1Hour(durationUpdater) 
            } else if(updateTime == "3hour") {
                runEvery3Hours(durationUpdater) 
            }
            schedule("1 0 0 ? * * *", durationFileMaint)
        }
    }
}

def durationHandler(evt) {
    checkEnableHandler()
    if(pauseApp || state.eSwitch) {
        log.info "${app.label} is Paused or Disabled"
    } else {
        if(logEnable) log.debug "------------------------------------------------------------------------------"
        if(logEnable) log.debug "In durationHandler (${state.version})"
        deviceStatus = evt.value
        deviceName = evt.displayName
        if(state.deviceStartMap == null) state.deviceStartMap = [:]
        if(state.eventList == null) state.eventList = []
        if(deviceStatus.toString() == aStatus.toString()) {
            def now = new Date()
            prev = now.getTime()
            state.deviceStartMap.put(deviceName, prev)
            if(logEnable) log.debug "In durationHandler - ${deviceName} became ${deviceStatus} at ${now}"
        } else {
            prev = state.deviceStartMap.get(deviceName, prev)
            if(prev) {
                def now = new Date()
                if(logEnable) log.debug "In durationHandler - ${deviceName} became ${deviceStatus} at ${now}"
                long unxNow = now.getTime()
                long unxPrev = prev
                unxNow = unxNow/1000 
                unxPrev = unxPrev/1000
                timeDiff = (unxNow-unxPrev)
                if(logEnable) log.debug "In durationHandler - ${deviceName}: timeDiff in Seconds: ${timeDiff}"
                ta = theAttr.toString().replace("[","").replace("]","")
                def nowStr = now.format("yyyy-MM-dd HH:mm:ss.SSS")
                listData = "${deviceName};${ta.capitalize()};${nowStr};${timeDiff};Final"                  
                state.eventList << listData
                state.deviceStartMap.remove(deviceName)
		durationUpdater()
                saveMapHandler()
            }
        }       
    }
}

def durationUpdater() {
        if(logEnable) log.debug "In durationUpdater ********** Updating Any Pending Duration **********"
        state.eventList.removeAll { it.toString().contains("Pending") } 
        if(logEnable) log.debug "In durationUpdater - Updating Pending Entries in File"
    
        if(theAttr) {
            theDevices.each { td ->
                def deviceName = td.getDisplayName()
                def prev = state.deviceStartMap.get(deviceName)
                if(prev) {
                    def now = new Date()
                    long unxNow = now.getTime()
                    long unxPrev = prev
                    unxNow = unxNow/1000 
                    unxPrev = unxPrev/1000
                    timeDiff = (unxNow-unxPrev)
                    if(logEnable) log.debug "In durationUpdater - ${deviceName}: timeDiff in Seconds: ${timeDiff}"
                    ta = theAttr.toString().replace("[","").replace("]","")
                    def nowStr = now.format("yyyy-MM-dd HH:mm:ss.SSS")
                    listData = "${deviceName};${ta.capitalize()};${nowStr};${timeDiff};Pending"                  
                    state.eventList << listData
                    // Keep deviceName in deviceStartMap, so can update again if needed
                    
                }       
                else if(logEnable) log.debug "In durationUpdater - ${deviceName}: Nothing in deviceStartMap"
            }
        }    
    saveMapHandler()
}

def durationFileMaint() {
    if(state.eventList) {
        if(logEnable) log.debug "------------------------------------------------------------------------------"
        if(logEnable) log.debug "In durationFileMaint (${state.version})"
        if(logEnable) log.debug "In durationFileMaint - OLD: $state.eventList"
        def today = new Date()
        
        if(logEnable) log.debug "In durationFileMaint ********** Finalizing file for yesterday **********"
        if(theAttr) {
            theDevices.each { td ->
                def deviceName = td.getDisplayName()
                def prev = state.deviceStartMap.get(deviceName)
                if(prev) {
                    def now = new Date()
                    def midnight = now.clearTime()
                    Calendar cal = Calendar.getInstance()
                    cal.setTimeZone(location.timeZone)
                    cal.setTime(midnight)
                    cal.add(Calendar.SECOND, -1)
                    Date lastNight = cal.getTime()
                    long unxNow = lastNight.getTime()
                    long unxPrev = prev
                    unxNow = unxNow/1000 
                    unxPrev = unxPrev/1000
                    timeDiff = (unxNow-unxPrev)
                    if(logEnable) log.debug "In durationFileMaint - ${deviceName}: timeDiff in Seconds: ${timeDiff} as of ${lastNight}"
                    ta = theAttr.toString().replace("[","").replace("]","")
                    def lastNightStr = lastNight.format("yyyy-MM-dd HH:mm:ss.SSS")
                    listData = "${deviceName};${ta.capitalize()};${lastNightStr};${timeDiff};Final"                  
                    state.eventList << listData
                    state.deviceStartMap.remove(deviceName)
                    state.deviceStartMap.put(deviceName, midnight.getTime()) // start tracking device attribute from beginning of day
                }               
            }
        }
        
        theDate = today - 8
        String tDate = theDate.format("yyyy-MM-dd")
        if(logEnable) log.debug "In durationFileMaint ********** Checking file for old data - tDate: $tDate **********"
        state.eventList.removeAll { it.toString().contains("${tDate}") }
        if(logEnable) log.debug "In durationFileMaint - NEW: $state.eventList"
        
        saveMapHandler()
    }
}

def getDataHandler(evt) {
    checkEnableHandler()
    if(pauseApp || state.eSwitch) {
        log.info "${app.label} is Paused or Disabled"
    } else {
        if(logEnable) log.debug "------------------------------------------------------------------------------"
        if(logEnable) log.debug "In getDataHandler (${state.version})"
        if(state.eventList == null) state.eventList = []
        if(state.lastMap == null) state.lastMap = [:]
        now = new Date().format("yyyy-MM-dd HH:mm:ss.SSS")
        theDevices.each { theDev ->
            theAttr.each { theAt ->
                if(theDev.hasAttribute(theAt)) {
                    event = theDev.currentValue(theAt)
                    theName = "${theDev}-${theAt}".replace(" ","")
                    try{
                        prev = state.lastMap.get(theName)
                        if(prev == null) prev = 999
                    } catch(e) {
                        prev = 999
                    }
                    def recordEvent = false
                    if(recordAll) recordEvent = true
                    else { 
                        if (state.isNumericalData) {                    
                            if(diffPerc) {                      
                                theDiff = 100 * ((Math.abs(event - prev)) / prev).round(2)
                            } else {
                                theDiff = Math.abs(prev - event)
                            }
                            if(logEnable) log.debug "In getDataHandler - checking prev: ${prev} -VS- ${event} - theDiff: ${theDiff}"
                            if(theDiff < diff.toInteger()) {
                                if (recordAll) recordEvent = true
                                else recordEvent = false
                            } else {
                               if(logEnable) log.debug "In getDataHandler - theDiff($theDiff) is > ${diff}"
                               recordEvent = true
                            }
                        }
                        else if (event != prev) recordEvent = true                            
                    }
                    if (recordEvent) {
                        if(logEnable) log.debug "In getDataHandler - recordAll - Recording ${theDev};${theAt.capitalize()};${now};${event} - prev: ${prev}"
                        listData = "${theDev};${theAt.capitalize()};${now};${event};Final"
                        state.eventList << listData
                        if(logEnable) log.debug "In getDataHandler - Saving prev - ${theName} - ${event}"
                        state.lastMap.put(theName,event)                    
                    }
                    else {
                        if(logEnable) log.debug "In getDataHandler - NOT Recording ${theDev};${theAt.capitalize()};${now};${event} - prev: ${prev}"    
                    }                        
                }
                log.trace "lastMap: ${state.lastMap}"
                checkPointsHandler()
            }
        }
        saveMapHandler()
    }
}

def checkPointsHandler() {
    int actualPoints = dataPoints * (theDevices.size() * theAttr.size())
    theCount = state.eventList.size()
    if(logEnable) log.debug "In checkPointsHandler - theCount: $theCount -VS- actualPoints: $actualPoints"
    if(theCount > actualPoints) {
        state.eventList.removeAt(0)           
    }
    theCount = state.eventList.size()
    if(logEnable) log.debug "In checkPointsHandler - NEW - theCount: $theCount -VS- actualPoints: $actualPoints"
    if(theCount > actualPoints) { checkPointsHandler() }
}

def saveMapHandler() {
    if(fName) {
        if(logEnable) log.debug "In getDataHandler - writting to file: $fName"
        writeFile(fName, state.eventList)
    } else {
        log.info "Quick Chart Data Collector - Please provide a File Name to save the data."
    }
}

def login() {        // Modified from code by @dman2306
    if(logEnable) log.debug "In login - Checking Hub Security"
    state.cookie = ""
    if(hubSecurity) {
        try{
            httpPost(
                [
                    uri: "http://127.0.0.1:8080",
                    path: "/login",
                    query: 
                    [
                        loginRedirect: "/"
                    ],
                    body:
                    [
                        username: hubUsername,
                        password: hubPassword,
                        submit: "Login"
                    ],
                    textParser: true,
                    ignoreSSLIssues: true
                ]
            )
            { resp ->
                if (resp.data?.text?.contains("The login information you supplied was incorrect.")) {
                    log.warn "Quick Chart Data Collector - username/password is incorrect."
                } else {
                    state.cookie = resp?.headers?.'Set-Cookie'?.split(';')?.getAt(0)
                }
            }
        } catch (e) {
            log.error(getExceptionMessageWithLine(e))
        }
    }
}

Boolean writeFile(fName, fData) {
    //if(logEnable) log.debug "Writing to file - ${fName} - ${fData}"
    login()
	try {
		def params = [
			uri: "http://127.0.0.1:8080",
			path: "/hub/fileManager/upload",
			query: [
				"folder": "/"
			],
			headers: [
				"Cookie": state.cookie,
				"Content-Type": "multipart/form-data; boundary=----WebKitFormBoundaryDtoO2QfPwfhTjOuS"
			],
			body: """------WebKitFormBoundaryDtoO2QfPwfhTjOuS
Content-Disposition: form-data; name="uploadFile"; filename="${fName}"
Content-Type: text/plain

${fData}

------WebKitFormBoundaryDtoO2QfPwfhTjOuS
Content-Disposition: form-data; name="folder"


------WebKitFormBoundaryDtoO2QfPwfhTjOuS--""",
			timeout: 300,
			ignoreSSLIssues: true
		]
		httpPost(params) { resp ->	
		}
	} catch (e) {
        log.error "Error writing file $fName: ${e}"
	}
}

Boolean getFileList(){
    login()
    if(logEnable) log.debug "----------------------------------------------------------------"
    if(logEnable) log.debug "In getFileList - Getting list of files"
    uri = "http://${location.hub.localIP}:8080/hub/fileManager/json";
    def params = [
        uri: uri,
        headers: [
            "Cookie": state.cookie,
        ]
    ]
    try {
        state.fileList = []
        httpGet(params) { resp ->
            if (resp != null && resp != []){
                if(logEnable) log.debug "In getFileList - Found some files"
                def json = resp.data
                if(json.toString().contains("html")) { json = [] }
                if(logEnable) log.debug "In getFileList - json: ${json}"
                if(json == []) {
                    //
                } else {
                    for (rec in json.files) {
                        //log.trace "rec: ${rec}"
                        fileType = rec.name[-3..-1]
                        if(fileType == "txt") {
                            state.fileList << rec.name
                        }
                    }
                }
            } else {
                if(logEnable) log.debug "In getFileList - No files found"
            }
        }
        if(logEnable) log.debug "In getFileList - fileList: ${state.fileList}"
    } catch (e) {
        log.error e
    }
}

def attributesHandler(theAttr) {
    attr = theAttr.toString().replace("[","").replace("]","")
    switch (attr) {
        case "acceleration":
            statusOptions = ["active", "inactive)"]
            break;
        case "contact": 
            statusOptions = ["open", "closed"]
            break;
        case "lock": 
            statusOptions = ["locked", "unlocked"]
            break;
        case "motion":  
            statusOptions = ["active","inactive"]  
            break;
        case "presence":
            statusOptions = ["present","not present"]
            break;
        case "switch":
            statusOptions = ["on","off"]
            break;
        case "valve":
            statusOptions = ["open","closed"]
            break;
        case "water":
            statusOptions = ["wet","dry"]
            break;
    }
    if(statusOptions == null) {
        state.message = "Attribute not supported (yet). Please let BPTWorld know what type of duration device you want to chart."
    } else {
        state.message = ""
    }
    return statusOptions
}

Boolean fileExists(){
    if(logEnable) log.debug "In fileExists - ${fName}"
    fileExists = false
    if(state.fileList) {
        state.fileList.each { fl ->
            if(logEnable) log.debug "In fileExists - Checking ${fl} -VS- ${fName}"
            if(fl.toString() == fName.toString()) {
                if(logEnable) log.trace "In fileExists - File Found! - ${fl}"
                fileExists = true
            }
        }
    } else {
        if(logEnable) log.debug "In fileExists - There are no files"
    }
    return fileExists
}
