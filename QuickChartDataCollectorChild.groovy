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

import groovy.json.*
import hubitat.helper.RMUtils
import java.util.TimeZone
import groovy.transform.Field
import groovy.time.TimeCategory
import java.text.SimpleDateFormat

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
                    if(attType) {
                        state.isNumericalData = attType.toLowerCase() == "number" ? true : false
                    }
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
                    ["3hour":"Every 3 Hours"],
                    ["custom":"Custom Time Period"]
                ], defaultValue:"statusChange", submitOnChange:true
                if (updateTime == "custom") {
                    input "customUpdateTimeValue", "number", title: "Period Value", submitOnChange:false, width: 2, required: true
                    input "customUpdateTimeUnits", "enum", title: "Period Units", options: ["mins":"minute(s)","hours":"hour(s)", "days":"day(s)"], submitOnChange:false, width: 2, required: true
                    input "customUpdateTimeStart", "time", title: "Period Start Time", submitOnChange:false, width: 2, required: true
                    paragraph getFormat("note", "Custom period will start at the next occurrence of the entered time of day, and recur every X minutes, hours, or days as entered.")
                }
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
                    ["3hour":"Every 3 Hours"],
                    ["custom":"Custom Time Period"]
                ], defaultValue:"manual", submitOnChange:true
                if (updateTime == "custom") {
                    input "customUpdateTimeValue", "number", title: "Period Value", submitOnChange:true, width: 2, required: true
                    input "customUpdateTimeUnits", "enum", title: "Period Units", options: ["mins":"minute(s)","hours":"hour(s)", "days":"day(s)"], submitOnChange:true, width: 2, required: true
                    input "customUpdateTimeStart", "time", title: "Period Start Time", submitOnChange:true, width: 2, required: true
                    paragraph getFormat("note", "Custom period will start at the next occurrence of the entered time of day, and recur every X minutes, hours, or days as entered.")
                }

                input "recordAll", "bool", title: "Record data even if it's the same as previous value", defaultValue:false, submitOnChange:true    
                if (theAttr) {
                    if (state.isNumericalData && !recordAll) {
                        input "diffPerc", "bool", title: "Use value difference (off) OR percentage difference (on)", defaultValue:false, submitOnChange:true
                        if(diffPerc) {
                            paragraph "* Using Percentage Difference"
                            input "diff", "decimal", title: "Difference from previous value to record a data point (range 0-100)", range: '0..100', defaultValue:0, submitOnChange:true
                        } else {
                            paragraph "* Using Value Difference"
                            input "diff", "decimal", title: "Difference from previous value to record a data point", defaultValue:0, submitOnChange:true
                        }
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
                    } else if (updateTime == "custom") {
                        Integer min = 0
                        if (customUpdateTimeUnits == "mins") min = (customUpdateTimeValue as Integer)
                        else if (customUpdateTimeUnits == "hours") min = (customUpdateTimeValue as Integer) * 60
                        else if (customUpdateTimeUnits == "days") min = (customUpdateTimeValue as Integer) * 60 * 24
                        hours = (min * dataPoints) / 60
                        if(dataPoints && theDevices && theAttr) {
                            paragraph "This will save ${hours} hours of data"
                            int actualPoints = dataPoints * (theDevices.size() * theAttr.size())
                            paragraph "Based on options selected: ${dataPoints} Datapoints x (${theDevices.size()} Device(s) x ${theAttr.size()} Attribute(s)) will be $actualPoints points of data saved."
                        }
                    } 
                    else {
                        min = updateTime.findAll( /\d+/ )*.toInteger()
                        min = min.toString().replace("[","").replace("]","")
                        if(updateTime == "1hour" || updateTime == "3hour") min = min.toInteger() * 60
                        hours = (min.toInteger() * dataPoints) / 60
                        if(dataPoints && theDevices && theAttr) {
                            paragraph "This will save ${hours} hours of data"
                            int actualPoints = dataPoints * (theDevices.size() * theAttr.size())
                            paragraph "Based on options selected: ${dataPoints} Datapoints x (${theDevices.size()} Device(s) x ${theAttr.size()} Attribute(s)) will be $actualPoints points of data saved."
                        }
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
            } else if(updateTime == "custom") {
                def startTime = toDateTime(customUpdateTimeStart)
                runOnce(startTime, periodicallyTriggerDataHandler)
            }

            schedule("59 59 23 ? * * *", rawDataFileMaint, [overwrite:false]) // record last value of the day
            schedule("1 0 0 ? * * *", rawDataFileMaint, [overwrite:false]) // record first value of the day
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
            } else if(updateTime == "custom") {
                def startTime = toDateTime(customUpdateTimeStart)
                runOnce(startTime, periodicallyTriggerDurationUpdater)
            }
            schedule("1 0 0 ? * * *", durationFileMaint)
        }
    }
}

def periodicallyTriggerDataHandler() {
    getDataHandler()
    Date then = getNextCustomPeriodTime()
    if (then) runOnce(then, periodicallyTriggerDataHandler)
}

def periodicallyTriggerDurationUpdater() {
    durationUpdater()
    Date then = getNextCustomPeriodTime()
    if (then) runOnce(then, periodicallyTriggerDurationUpdater)
}

Date getNextCustomPeriodTime() {
    Date now = new Date()
    Date then = null
    if (customUpdateTimeUnits == "mins") {
        use( TimeCategory ) {
            then = now + (customUpdateTimeValue as Integer).minutes
        }
    }
    else if (customUpdateTimeUnits == "hours") {
        use( TimeCategory ) {
            then = now + (customUpdateTimeValue as Integer).hours
        }
    }
    else if (customUpdateTimeUnits == "days") {
        use( TimeCategory ) {
            then = now + (customUpdateTimeValue as Integer).days
        }
    }
    return then
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
                                theDiff = 100 * ((Math.abs(event - prev)) / prev).round(4)
                            } else {
                                theDiff = Math.abs(prev - event)
                            }
                            if(logEnable) log.debug "In getDataHandler - checking prev: ${prev} -VS- ${event} - theDiff: ${theDiff}"
                            if(theDiff < diff) {
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

def rawDataFileMaint() {
    checkEnableHandler()
    if(pauseApp || state.eSwitch) {
        log.info "${app.label} is Paused or Disabled"
    } else {
        if(logEnable) log.debug "------------------------------------------------------------------------------"
        if(logEnable) log.debug "In rawDataFileMaint (${state.version})"
        if(state.eventList == null) state.eventList = []
        if(state.lastMap == null) state.lastMap = [:]
        now = new Date().format("yyyy-MM-dd HH:mm:ss.SSS")
        theDevices.each { theDev ->
            theAttr.each { theAt ->
                if(theDev.hasAttribute(theAt)) {
                    event = theDev.currentValue(theAt)
                    theName = "${theDev}-${theAt}".replace(" ","")
                    // record attribute values at the start and end of the day
                     if(logEnable) log.debug "In rawDataFileMaint - Recording ${theDev};${theAt.capitalize()};${now};${event}"
                     listData = "${theDev};${theAt.capitalize()};${now};${event};Final"
                     state.eventList << listData
                     if(logEnable) log.debug "In getDataHandler - Saving prev - ${theName} - ${event}"
                     state.lastMap.put(theName,event)                                          
                }
                log.trace "lastMap: ${state.lastMap}"
                checkPointsHandler()
            }
        }
        saveMapHandler()
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


def checkHubVersion() {
    hubVersion = getHubVersion()
    hubFirmware = location.hub.firmwareVersionString
    if(logEnable) log.debug "In checkHubVersion - Info: ${hubVersion} - ${hubFirware}"
}

def parentCheck(){  
	state.appInstalled = app.getInstallationState() 
	if(state.appInstalled != 'COMPLETE'){
		parentChild = true
  	} else {
    	parentChild = false
  	}
}

def appControlSection() {
    input "pauseApp", "bool", title: "Pause App", defaultValue:false, submitOnChange:true
    if(pauseApp) {
        if(app.label) {
            if(!app.label.contains("(Paused)")) {
                app.updateLabel(app.label + " <span style='color:red'>(Paused)</span>")
            }
        }
    } else {
        if(app.label) {
            if(app.label.contains("(Paused)")) {
                app.updateLabel(app.label - " <span style='color:red'>(Paused)</span>")
            }
        }
    }
    if(pauseApp) { 
        paragraph app.label
    } else {
        label title: "Enter a name for this automation", required:true
    }
}

def appGeneralSection() {
    input "logEnable", "bool", title: "Enable Debug Options", description: "Log Options", defaultValue:false, submitOnChange:true
    if(logEnable) {
        input "logOffTime", "enum", title: "Logs Off Time", required:false, multiple:false, options: ["1 Hour", "2 Hours", "3 Hours", "4 Hours", "5 Hours", "Keep On"]
    }
    paragraph "This app can be enabled/disabled by using a switch. The switch can also be used to enable/disable several apps at the same time."
    input "disableSwitch", "capability.switch", title: "Switch Device(s) to Enable / Disable this app <small>(When selected switch is ON, app is disabled.)</small>", submitOnChange:true, required:false, multiple:true
}

def createDeviceSection(driverName) {
    paragraph "This child app needs a virtual device to store values."
    input "useExistingDevice", "bool", title: "Use existing device (off) or have one created for you (on)", defaultValue:false, submitOnChange:true
    if(useExistingDevice) {
        input "dataName", "text", title: "Enter a name for this vitual Device (ie. 'Front Door')", required:true, submitOnChange:true
        paragraph "<b>A device will automatically be created for you as soon as you click outside of this field.</b>"
        if(dataName) createDataChildDevice(driverName)
        if(statusMessageD == null) statusMessageD = "Waiting on status message..."
        paragraph "${statusMessageD}"
    }
    input "dataDevice", "capability.actuator", title: "Virtual Device specified above", required:true, multiple:false, submitOnChange:true
    if(!useExistingDevice) {
        app.removeSetting("dataName")
        paragraph "<small>* Device must use the '${driverName}'.</small>"
    }
}

def createDataChildDevice(driverName) {    
    if(logEnable) log.debug "In createDataChildDevice (${state.version})"
    statusMessageD = ""
    if(!getChildDevice(dataName)) {
        if(logEnable) log.debug "In createDataChildDevice - Child device not found - Creating device: ${dataName}"
        try {
            addChildDevice("BPTWorld", driverName, dataName, 1234, ["name": "${dataName}", isComponent: false])
            if(logEnable) log.debug "In createDataChildDevice - Child device has been created! (${dataName})"
            statusMessageD = "<b>Device has been been created. (${dataName})</b>"
        } catch (e) { if(logEnable) log.debug "Unable to create device - ${e}" }
    } else {
        statusMessageD = "<b>Device Name (${dataName}) already exists.</b>"
    }
    return statusMessageD
}

def uninstalled() {
    sendLocationEvent(name: "updateVersionInfo", value: "${app.id}:remove")
	removeChildDevices(getChildDevices())
}

private removeChildDevices(delete) {
	delete.each {deleteChildDevice(it.deviceNetworkId)}
}

def speechSection() {
    
}

def letsTalk(msg) {
    if(logEnable) log.debug "In letsTalk (${state.version}) - Sending the message to Follow Me - msg: ${msg}"
    if(useSpeech && fmSpeaker) {
        fmSpeaker.latestMessageFrom(state.name)
        fmSpeaker.speak(msg,null)
    }
}

def pushHandler(msg){
    if(logEnable) log.debug "In pushNow (${state.version}) - Sending a push - msg: ${msg}"
    if(pushICN) {
        theMessage = "${app.label} - ${msg}"
    } else {
        theMessage = "${msg}"
    }
    if(logEnable) log.debug "In pushNow - Sending message: ${theMessage}"
    sendPushMessage.deviceNotification(theMessage)
}

def useWebOSHandler(msg){
    if(logEnable) log.debug "In useWebOSHandler (${state.version}) - Sending to webOS - msg: ${msg}"
    useWebOS.deviceNotification(msg)
}

// ********** Normal Stuff **********
def logsOff() {
    log.info "${app.label} - Debug logging auto disabled"
    app.updateSetting("logEnable",[value:"false",type:"bool"])
}

def checkEnableHandler() {
    setVersion()
    state.eSwitch = false
    if(disableSwitch) { 
        if(logEnable) log.debug "In checkEnableHandler - disableSwitch: ${disableSwitch}"
        disableSwitch.each { it ->
            theStatus = it.currentValue("switch")
            if(theStatus == "on") { state.eSwitch = true }
        }
        if(logEnable) log.debug "In checkEnableHandler - eSwitch: ${state.eSwitch}"
    }
}

def getImage(type) {					// Modified from @Stephack Code
    def loc = "<img src=https://raw.githubusercontent.com/bptworld/Hubitat/master/resources/images/"
    if(type == "Blank") return "${loc}blank.png height=40 width=5}>"
    if(type == "checkMarkGreen") return "${loc}checkMarkGreen2.png height=30 width=30>"
    if(type == "optionsGreen") return "${loc}options-green.png height=30 width=30>"
    if(type == "optionsRed") return "${loc}options-red.png height=30 width=30>"
    if(type == "instructions") return "${loc}instructions.png height=30 width=30>"
    if(type == "logo") return "${loc}logo.png height=40>"
}

def getFormat(type, myText=null, page=null) {			// Modified code from @Stephack
    if(type == "header-green") return "<div style='color:#ffffff;font-weight: bold;background-color:#81BC00;border: 1px solid #000000;box-shadow: 2px 3px #8B8F8F;border-radius: 10px'>${myText}</div>"
    if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;' />"
    if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
    
    if(type == "button-blue") return "<a style='color:white;text-align:center;font-size:20px;font-weight:bold;background-color:#03FDE5;border:1px solid #000000;box-shadow:3px 4px #8B8F8F;border-radius:10px' href='${page}'>${myText}</a>"
}

def display(data) {
    if(data == null) data = ""
    if(app.label) {
        if(app.label.contains("(Paused)")) {
            theName = app.label - " <span style='color:red'>(Paused)</span>"
        } else {
            theName = app.label
        }
    }
    if(theName == null || theName == "") theName = "New Child App"
    if(!state.name) { state.name = "" }
    if(state.name == theName) {
        headerName = state.name
    } else {
        headerName = "${state.name} - ${theName}"
    }
}

def display2() {
    setVersion()
    section() {
        if(state.appType == "parent") { href "removePage", title:"${getImage("optionsRed")} <b>Remove App and all child apps</b>", description:"" }
        paragraph getFormat("line")
        if(state.version) {
            bMes = "<div style='color:#1A77C9;text-align:center;font-size:20px;font-weight:bold'>${state.name} - ${state.version}"
        } else {
            bMes = "<div style='color:#1A77C9;text-align:center;font-size:20px;font-weight:bold'>${state.name}"
        }
        bMes += "</div>"
        paragraph "${bMes}"
        paragraph "<div style='color:#1A77C9;text-align:center'>BPTWorld<br>Donations are never necessary but always appreciated!<br><a href='https://paypal.me/bptworld' target='_blank'><img src='https://raw.githubusercontent.com/bptworld/Hubitat/master/resources/images/pp.png'></a></div>"
    }
}
