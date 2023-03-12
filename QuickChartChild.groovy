/**
 *  **************** Quick Chart Child App  ****************
 *
 *  Design Usage:
 *  Chart your data, quickly and easily. Display your charts in any dashboard.
 *
 *  Copyright 2022-2023 Bryan Turcotte (@bptworld)
 * 
 *  This App is free. If you like and use this app, please be sure to mention it on the Hubitat forums! Thanks.
 *
 *  Remember...I am not a professional programmer, everything I do takes a lot of time and research!
 *  Donations are never necessary but always appreciated. Donations to support development efforts are accepted via: 
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
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  Changes:
 *  0.5.0 - 02/22/22 - Reorganize User Interface to be more flexible for other chart types; Added support for radial gauge chart and progress bar; Added user-defined chart height; Define custom states with numeric ranges; separate from library
 *  0.4.3 - 02/17/22 - Bug fixes; X-Axis origin; Persistent last data point optional; Update chart with device attribute value; Custom bar thickness
 *  0.4.2 - 12/01/22 - Fixes a minor bug
 *  0.4.1 - 11/02/22 - Added Bar Chart Width Configurabiity; Improved Legend Configurability - @JustinL
 *  0.4.0 - 11/01/22 - Bug Fix - @JustinL
 *  ---
 *  0.0.1 - 07/12/22 - Initial release.
 */

import groovy.json.*
import hubitat.helper.RMUtils
import java.util.TimeZone
import groovy.transform.Field
import groovy.time.TimeCategory
import java.text.SimpleDateFormat

def setVersion(){
    state.name = "Quick Chart"
	state.version = "0.5.0"
}

def syncVersion(evt){
    setVersion()
    sendLocationEvent(name: "updateVersionsInfo", value: "${state.name}:${state.version}")
}

definition(
    name: "Quick Chart Child",
    namespace: "BPTWorld",
    author: "Bryan Turcotte, Justin Leonard",
    description: "Chart your data, quickly and easily. Display your charts in any dashboard.",
    category: "Convenience",
	parent: "BPTWorld:Quick Chart",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
	importUrl: "",
)

preferences {
    page(name: "pageConfig")
}

def pageConfig() {
    dynamicPage(name: "", title: "", install: true, uninstall: true) {
		display() 
        section("${getImage('instructions')} <b>Instructions:</b>", hideable: true, hidden: true) {
			paragraph "<b>Notes:</b>"
    		paragraph "Chart your data, quickly and easily. Display your charts in any dashboard."
		}
        
        section(getFormat("header-green", "${getImage("Blank")}"+" App Control")) {
            appControlSection()
        }
        
        section(getFormat("header-green", "${getImage("Blank")}"+" Virtual Device")) {
            createDeviceSection("Quick Chart Driver")           
            paragraph "Since the virtual device gets deleted if this child app is deleted, each child app needs to have a unique virtual device to store the chart."
        }
        def chartConfigType = null
        def axisType = null
        
        section(getFormat("header-green", "${getImage("Blank")}"+" Genearl Chart Options")) {
            input "gType", "enum", title: "Chart Style", options: ["bar","line", "horizontalBar","stateTiming","pie","doughnut","scatter","bubble","gauge","radialGauge","violin","sparkline","progressBar","radialProgressGauge",""], submitOnChange:true, width:4, required: true
            chartConfigType = getChartConfigType(gType)
            axisType = getChartAxisType(gType)
            
            input "theChartTitle", "text", title: "Chart Title", submitOnChange:true, width:4   
            input "chartHeight", "number", title: "Chart Height (pixels)", description: "Leave Blank for Default Height", submitOnChange:false, width: 4       
            input "chartPadding", "number", title: "Chart Padding (pixels)", description: "Leave Blank for Default Padding. Pad if labels get cut off.", submitOnChange:false, width: 4   
            input "bkgrdColor", "text", title: "Background Color", defaultValue:"white", submitOnChange:false, width: 4
            input "labelColor", "text", title: "Label Color", defaultValue:"black", submitOnChange:false, width: 4
            input "labelSize", "number", title: "Label size (pixels)", submitOnChange:false, width: 4


            if (hasGrid(gType)) input "gridColor", "text", title: "Grid Color", defaultValue:"black", submitOnChange:false, width: 4   
            if (hasBar(gType)) {
                input "barColor", "text", title: "Bar Color", defaultValue:"blue", submitOnChange:false, width: 4  
                input "globalBarThickness", "number", title: "Global Bar Thickness", submitOnChange:false, width: 4, required: false, defaultValue: 30           
            }

            def updateTimeOptions = [
                ["manual":"Manual"],
                ["5min":"Every 5 Minutes"],
                ["10min":"Every 10 Minutes"],
                ["15min":"Every 15 Minutes"],
                ["30min":"Every 30 Minutes"],
                ["1hour":"Every 1 Hour"],
                ["3hour":"Every 3 Hours"],
            ]
            if (dataSource || chartConfigType == "pointData") updateTimeOptions.add(["realTime":"Real Time"])
            else updateTimeOptions.add(["attribute":"With Device Attribute Value"])
            input "updateTime", "enum", title: "When to Update", options: updateTimeOptions, defaultValue:"manual", submitOnChange:true, width: 4 
            if (updateTime == "attribute") {
                input "updateDevice", "capability.*", title: "Select Update Device", multiple:false, submitOnChange:true, width: 12, required: true
                if (updateDevice) {
                    allAttrs = []
                    attributes = updateDevice.supportedAttributes
                    attributes.each { att ->
                       allAttrs << att.name
                    }
                    devAtt = allAttrs.unique().sort()
                    input "updateAttribute", "enum", title: "Select Update Attribute", options: devAtt, multiple:false, submitOnChange:true, width: 4, required: true
                }
                if (updateDevice && updateAttribute) input "updateAttributeCondition", "enum", title: "Update When Attribute...", options: ["value" : "Change To Certain Value", "changes" : "Changes to ANY Value"], defaultValue:"changes", submitOnChange:true, width: 4, required: true
                if (updateDevice && updateAttribute && updateAttributeCondition == "value") input "updateAttributeValue", "text", title: "Attribute Value that triggers chart update", submitOnChange:false, width: 4, required: true
            }
        }

        if (chartConfigType == "pointData") pointDataChartConfig() 
        else if (chartConfigType == "comparisonData") comparisonDataChartConfig()
        else if (chartConfigType == "seriesData") seriesDataChartConfig()

        if (axisType == "xy") {
            section(getFormat("header-green", "${getImage("Blank")}"+" Axis Configuration")) {
                XYAxisConfig()
            }
        }

       section(getFormat("header-green", "${getImage("Blank")}"+" Chart Preview")) {
            input "makeGraph", "bool", title: "Manually Trigger a Chart", defaultValue:false, submitOnChange:true
            if(makeGraph) {
                getEvents()
                app.updateSetting("makeGraph",[value:"false",type:"bool"])
            }
            if(buildChart) {
                paragraph "${buildChart}"
            } else {
                paragraph ""
            }
        }
        
        section(getFormat("header-green", "${getImage("Blank")}"+" General")) {
            appGeneralSection()
        }
		display2()
	}
}

def getChartConfigType(gType) {
    def configType = "seriesData"
    if (gType == "radialGauge" || gType == "gauge" || gType == "progressBar" || gType == "radialProgressGauge") configType = "pointData"
    else if (gType == "pie" || gType == "doughnut") configType = "comparisonData"
    return configType
}

def getChartAxisType(gType) {
    def axisType = "xy"
    if (gType == "radialGauge" || gType == "gauge" || gType == "radialProgressGauge" || gType == "pie" || gType == "doughnut" || gType == "polarArea" || gType == "radar") axisType = "circular"
    if (gType == "progressBar") axisType = "linear"
    return axisType
}

def hasGrid(gType) {
    def hadGrid = true
    if (gType == "radialGauge" || gType == "gauge" || gType == "progressBar" || gType == "radialProgressGauge" || gType == "pie" || gType == "doughnut") hasGrid = false
    return hasGrid
}

def hasBar(gType) {
    def hasBar = false
    if (gType == "stateTiming" || gType == "bar" || gType == "horizontalBar" || gType == "progressBar") hasBar = true
    return hasBar

}

def seriesDataChartConfig() {
    section(getFormat("header-green", "${getImage("Blank")}"+" Label & Legend Configuration")) {
        input "onChartValueLabels", "bool", title: "Show Attribute Values as On-Chart Labels", defaultValue:false, submitOnChange:false, width: 4
        input "dFormat", "bool", title: "Use 24-hour timestamps", defaultValue:false, submitOnChange:true, width: 4
        input "displayLegend", "bool", title: "Show Legend", defaultValue:true, submitOnChange:false, width: 4
        input "showDevInAtt", "bool", title: "Show Device Name in Legend", defaultValue:false, submitOnChange:true, width: 4
        if(showDevInAtt) {
            //paragraph "To save characters, enter in filters to remove characters from each device name.<br><small>ie. Motion;on Hub-Device;Sensor;Contact</small>"
            //input "devFilters", "text", title: "Filters (separtate each with a ; (semicolon))", required:true, submitOnChange:true
            input "showAtt", "bool", title: "Show Attribute in Legend", defaultValue:true, submitOnChange:false, width: 4
        }   
        input "legendBoxWidth", "number", title: "Legend Box Width", width: 4, defaultValue: 40
        input "legendFontSize", "number", title: "Legend Font Size", width: 4, defaultValue: 12    
    }
    
    section(getFormat("header-green", "${getImage("Blank")}"+" Data Configuration")) {
        input "dataSource", "bool", title: "Get data from file (off) OR from device event history (on)", defaultValue:false, submitOnChange:true
        if(dataSource) {        // Event History
            paragraph "<b>Using Device History</b><br>"
            deviceInput(true, true) 
    } else {
        paragraph "<b>Using Data File</b><br><small>Data files are made using the 'Quick Chart Data Collector'</small>"
        input "hubSecurity", "bool", title: "Using Hub Security", defaultValue:false, submitOnChange:true
        if(hubSecurity) {
            input "hubUsername", "string", title: "Hub Security username", submitOnChange:true
            input "hubPassword", "password", title: "Hub Security password", submitOnChange:true
        } else {
            app.removeSetting("hubUsername")
            app.removeSetting("hubPassword")
        }
        getFileList()
        if(fileList) {
            input "fName", "enum", title: "Select a File", options: fileList, multiple:false, submitOnChange:true, required: true
        } else {
            paragraph "If file list is empty. Be sure to enter in your Hub Security crediantials and then flip this switch."
            input "getList", "bool", title: "Get List", defaultValue:false, submitOnChange:true
            if(getList) {
                app.updateSetting("getList",[value:"false",type:"bool"])
            }
        }
                    
        def dataTypeOptions = []
        if (gType != "stateTiming") {
            dataTypeOptions = [
                ["rawdata":"Raw Data Over Time - Temp,Humidity,etc."],
                ["duration":"How long things have been on/open/active/etc per xx"]
            ]    
        }
        else dataTypeOptions = [["rawdata":"Raw Data Over Time - Temp,Humidity,etc."]]       // force charting raw data if chart type is stateTiming   
        input "dataType", "enum", title: "Select the type of data", options: dataTypeOptions, submitOnChange:true, required: true

        }
                
    paragraph "<hr>"
    if(dataType == "rawdata") {
        input "theDays", "enum", title: "Select Days to Chart<br><small>* Remember to check how many data points are saved, per attribute, for device selected.</small>", multiple:false, required:true, options: [
            ["999":"Today"],
            ["1":"+ 1 Day"],
            ["2":"+ 2 Days"],
            ["3":"+ 3 Days"],
            ["4":"+ 4 Days"],
            ["5":"+ 5 Days"],
            ["6":"+ 6 Days"],
            ["7":"+ 7 Days"]
        ], defaultValue:"999", submitOnChange:true
        if (state.isNumericalData) input "decimals", "enum", title: "Number of Decimal places", options: ["None","1","2"], defaultValue:"None", submitOnChange:true                
    } else if(dataType == "duration") {
        input "theDays", "enum", title: "Select How to Chart", multiple:false, required:true, options: [
            ["999":"Today"],
            ["1":"Daily Total - 1 Day"],
            ["2":"Daily Total - 2 Days"],
            ["3":"Daily Total - 3 Days"],
            ["4":"Daily Total - 4 Days"],
            ["5":"Daily Total - 5 Days"],
            ["6":"Daily Total - 6 Days"],
            ["7":"Daily Total - 7 Days"]
        ], defaultValue:"7", submitOnChange:true
        input "decimals", "enum", title: "Number of Decimal places", options: ["None","1","2"], defaultValue:"None", submitOnChange:true, width:6
        input "secMin", "bool", title: "Chart using Seconds (off) or Minutes (on)", defaultValue:false, submitOnChange:true, width:6
        }
        
        if(dataType == "rawdata" && (gType == "stateTiming" || state.isNumericalData == false)) {
                    
            input "extrapolateDataToCurrentTime", "bool", title: "Last Datapoint persists until Current Time?", submitOnChange:true, width: 12, defaultValue: true                  
            if (!customizeStates) paragraph "<small><b>Using Default State Colors</b><br>Green: active, open, locked, present, on, open, true, dry<br>Red: inactive, closed, unlocked, not present, off, closed, false, wet</small>"
            if (gType == "stateTiming") customStateInput(true, false)
        }
        if (gType != "stateTiming") input "reverseMap", "bool", title: "Reverse Data Ordering", defaultValue:false, submitOnChange:true
        // force reverseMap for stateTiming
    }
}

def customStateInput(onlyByValue = false, requireRangeValue = false) {
    def customizeBar = hasBar(gType) && gType != "progressBar"
    def inputWidth = customizeBar ? 4 : 6
    input "customizeStates", "bool", title: "Customize State Colors ${customizeBar ? 'and/or Bar Thickness?' : ''}", defaultValue:false, submitOnChange:true, width: 12
    if (customizeStates) {    
        if (!onlyByValue) input "customStateCriteria", "enum", title: "Customize States By...", options: ["Value","Device","Attribute"], submitOnChange:true, width:6
        def instructions = "<small>State Color" + (customizeBar ? ' and Bar Thickness' : '') + " is set to the color" + (customizeBar ? ' and bar thickness' : '') + " specified for whatever state matches first, overriding any global settings specified above. </small>"
        if (customStateCriteria == "Value" || onlyByValue) {
            input "numStates", "number", title: "How many states?", defaultValue:2, submitOnChange:true, width: 6
            if (requireRangeValue) instructions += "<small> States must be defined in terms of a range of numeric values, as MIN:MAX (example: 1:50). Ranges are inclusive of both MIN and MAX. </small>"
            else instructions += "<small> States can be defined as a single text value, a single numeric value, or a range of numeric values. Define a range of numeric values as MIN:MAX (example: 1:50). Ranges are inclusive of both MIN and MAX. </small>"
            paragraph instructions
            if (!numStates) app.updateSetting("numStates",[type:"number",value:2]) 
            if (numStates) {
                for (i=1; i <= numStates; i++) {
                    input "state${i}", "text", title: "State ${i}", submitOnChange:false, width: inputWidth
                    input "state${i}Color", "text", title: "Color", submitOnChange:false, width: inputWidth
                    if (customizeBar) input "state${i}BarThickness", "number", title: "Bar Thickness", defaultValue: 30, submitOnChange:false, width: 4
                }
            }      
        }
        else if (customStateCriteria == "Device" && settings["theDevice"]) { 
           paragraph instructions
            for (i=1; i <= settings["theDevice"].size(); i++) {
                def sanitizedDevice = settings["theDevice"][i-1].replaceAll("\\s","").toLowerCase()
                input "state${sanitizedDevice}Color", "text", title: settings["theDevice"][i-1] + "Color", submitOnChange:false, width: inputWidth
                if (customizeBar) input "state${sanitizedDevice}BarThickness", "number", title: settings["theDevice"][i-1] + "Bar Thickness", defaultValue: 30, submitOnChange:false, width: inputWidth
            }
        }
        else if (customStateCriteria == "Attribute" && settings["theAtt"]) { 
           paragraph instructions
            for (i=1; i <= settings["theAtt"].size(); i++) {
                def sanitizedAtt = settings["theAtt"][i-1].replaceAll("\\s","").toLowerCase()
                input "state${sanitizedAtt}Color", "text", title: settings["theAtt"][i-1] + " Color", submitOnChange:false, width: inputWidth
                if (customizeBar) input "state${sanitizedAtt}BarThickness", "number", title: settings["theAtt"][i-1] + " Bar Thickness", defaultValue: 30, submitOnChange:false, width: inputWidth
            }
        }  
    }

}

def XYAxisConfig() {
    if (gType != "stateTiming" || state.isNumericalData == true) input "yMinValue", "text", title: "Specify Min Value to Chart<br><small>* If blank, chart uses the smallest value found in dataset.</small>", submitOnChange:true
            
     if(dataType == "rawdata" && (gType == "stateTiming" || state.isNumericalData == false)) {
                
          def xAxisOriginOptions = [
              ["data":"with Data"],
              ["day":"with Day"]
          ]
                    
          if (gType != "stateTiming" && reverseMap == false) {
              xAxisOriginOptions = [
                  ["data":"with Data"],
                  ["currentTime":"with Current Time"],
                  ["day":"with Day"]
               ]
          }
                
          input "xAxisOrigin", "enum", title: "X-Axis Originates", options: xAxisOriginOptions, defaultValue:"data", submitOnChange:true, width: 6
 
          input "xAxisTerminal", "enum", title: "X-Axis Terminates", options: [
               ["data":"with Data"],
               ["currentTime":"with Current Time"],
               ["day":"with Day"]
          ], defaultValue:"currentTime", submitOnChange:true, width: 6
                    
          if (gType != "stateTiming" || state.isNumericalData == true)  {
               input "chartXAxisAsTime", "bool", title: "Chart X-Axis as Date/Time?", submitOnChange:false, width: 12, defaultValue: true
               if (chartXAxisAsTime) {
                   input "xAxisTimeUnit", "enum", title: "X-Axis Time Unit", options: ["second", "minute", "hour", "day", "week", "month", "quarter", "year"], submitOnChange:false, required: false, width: 6
                   input "xAxisTimeFormat", "text", title: "X-Axis Time Format", description: "Allowable Formats https://momentjs.com/docs/#/displaying/format/", submitOnChange:false, width: 6, required: false
                }
          }
          if (gType == "stateTiming" || state.isNumericalData == false)  {
                input "xAxisTimeUnit", "enum", title: "X-Axis Time Unit", options: ["second", "minute", "hour", "day", "week", "month", "quarter", "year"], submitOnChange:false, required: false, width: 6
                input "xAxisTimeFormat", "text", title: "X-Axis Time Format", description: "https://momentjs.com/docs/#/displaying/format/", submitOnChange:false, width: 6, required: false
          }
     }
    else {
        input "showStaticLine", "bool", title: "Show Static Line", submitOnChange:true, width: 12
        if (showStaticLine) {
                input "staticLineValue", "number", title: "Static Line Value", submitOnChange:false, width: 6, required: true
                input "staticLineLabel", "text", title: "Static Line Label", submitOnChange:false, width: 6, required: false
                input "staticLineColor", "text", title: "Static Line Color", submitOnChange:false, width: 6, required: false
                input "staticLineWidth", "text", title: "Static Line Width (number)", submitOnChange:false, width: 6, required: false                     
        }
        input "showDynamicLine", "bool", title: "Show Dynamic Line", submitOnChange:true, width: 12
        if (showDynamicLine) {
            input "dynamicLineSource", "enum", title: "Select the source for the dynamic line", options: ["Device Attribute Value", "Charted Value Average"], submitOnChange: true, required: true, width: 6
            if (dynamicLineSource == "Charted Value Average") paragraph "<small>Note: Charted Value Average is Calculated Across All Devices and All Attributes</small>"
            if (dynamicLineSource == "Device Attribute Value") {
                input "dynamicLineDevice", "capability.*", title: "Select the Dynamic Line Device", submitOnChange:true, required: true, width: 6
                if(dynamicLineDevice) {
                    def attrs = []
                    dynamicLineDevice.supportedAttributes.each { att ->                             
                        if(att && att.getDataType().toLowerCase() == "number") attrs << att.name
                    }
                    input "dynamicLineAttribute", "enum", title: "Select the Dynamic Line Attribute", options: attrs.unique().sort(), submitOnChange:false, required: true, width: 6
                }
            }
            else if (dynamicLineSource == "Charted Value Average") { 
                // nothing to do
                // will calculate average across the charted values
            }
            input "dynamicLineLabel", "text", title: "Dynamic Line Label", submitOnChange:false, width: 4, required: false
            input "dynamicLineColor", "text", title: "Dynamic Line Color", submitOnChange:false, width: 4, required: false
            input "dynamicLineWidth", "text", title: "Dynamic Line Width (number)", submitOnChange:false, width: 4, required: false                     
        }
    }      

    def inputWidth = gType != "stateTiming" ? 4 : 6
    
    input "displayXAxis", "bool", title: "Show X-Axis", defaultValue:true, submitOnChange:false, width: inputWidth
    input "displayXAxisGrid", "bool", title: "Show X-Axis Gridlines", defaultValue:true, submitOnChange:false, width: inputWidth
    if (gType != "stateTiming") input "stackXAxis", "bool", title: "Stack X-Axis Data", defaultValue:false, submitOnChange:false, width: 4            
    
    input "displayYAxis", "bool", title: "Show Y-Axis", defaultValue:true, submitOnChange:false, width: inputWidth
    input "displayYAxisGrid", "bool", title: "Show Y-Axis Gridlines", defaultValue:true, submitOnChange:false, width: inputWidth
    if (gType != "stateTiming") input "stackYAxis", "bool", title: "Stack Y-Axis Data", defaultValue:false, submitOnChange:false, width: 4
  
    input "tickSource", "enum", title: "Tick Source", options: ["auto", "data"], submitOnChange: false, required: true, width: 3

}

def pointDataChartConfig() {
    if (gType == "radialGauge") {
        section(getFormat("header-green", "${getImage("Blank")}"+" Radial Gauge Configuration")) {
            input "centerFillColor", "text", title: "Center Background Color", defaultValue:"white", submitOnChange: false, width: 6
            input "centerImage", "text", title: "Center Background Image", description: "Overrides any specified center color", defaultValue:"", submitOnChange: false, width: 6
            input "centerSubText", "text", title: "Center Subtext", defaultValue:"", submitOnChange: false, width: 6
            input "centerPercentage", "number", title: "Center Size (Percentage)", width: 6
            input "trackColor", "text", title: "Track Background Color", defaultValue:"gray", submitOnChange:false, width: 6
            input "trackFillColor", "text", title: "Track Fill Color", defaultValue:"green", submitOnChange: false, width: 6
            input "arcBorderWidth", "number", title: "Outline Width", width: 6, defaultValue: 0
            input "arcBorderColor", "text", title: "Outline Color", width: 6, defaultValue: ""
            input "roundedCorners", "bool", title:"Rounded Corners?", width: 6, required: true, defaultValue: false
        }
        section(getFormat("header-green", "${getImage("Blank")}"+" Chart Data Configuration")) {
            deviceInput(false, false)
            input "valueUnits", "text", title: "Add Value Units Suffix", defaultValue:"", submitOnChange: false, width: 4
            input "domainMin", "number", title: "Minimum Possible Value", submitOnChange:false, width: 4, required: true
            input "domainMax", "number", title: "Maximum Possible Value", submitOnChange:false, width: 4, required: true
            customStateInput(true)
        }
    }
    else if (gType == "progressBar") {
        section(getFormat("header-green", "${getImage("Blank")}"+" Progress Bar Configuration")) {
            input "progressTrackColor", "text", title: "Progress Track Color", defaultValue:"gray", submitOnChange: false, width: 4
        }
        section(getFormat("header-green", "${getImage("Blank")}"+" Chart Data Configuration")) {
            deviceInput(false, false) 
            input "valueUnits", "text", title: "Add Value Units Suffix", defaultValue:"%", submitOnChange: false, width: 6
            input "maxProgress", "number", title: "Maximum Progress Value", width: 6, defaultValue: 100
            customStateInput(true)
        }
    }
    else if (gType == "radialProgressGauge") {
        section(getFormat("header-green", "${getImage("Blank")}"+" Radial Progress Gauge Configuration")) {
            input "centerPercentage", "number", title: "Center Size (Percentage)", width: 4
            input "circumference", "decimal", title: "Circumference (* pi)", width: 4
            input "rotation", "decimal", title: "Rotation (* pi)", width: 4
        }
        section(getFormat("header-green", "${getImage("Blank")}"+" Chart Data Configuration")) {
            deviceInput(false, false, false, false)    
            paragraph "<small> Minimum possible value is fixed at 0. </small>"
            input "domainMax", "number", title: "Maximum Possible Value", submitOnChange:false, width: 4, required: true
        }
        section(getFormat("header-green", "${getImage("Blank")}"+" Progress Range Configuration")) {
            input "showProgressRanges", "bool", title: "Define Range(s) of Progress with Range-Specific Color(s)?", defaultValue:false, submitOnChange:true, width: 12
            if (showProgressRanges) {
                input "numRanges", "number", title: "How many ranges?", defaultValue:3, submitOnChange:true, width: 6
                paragraph "<small> Ranges cannot overlap with one another. There must be one range defined with a lower bound of 0. Define a range as MIN:MAX (example: 0:50). Ranges are inclusive of both MIN and MAX. </small>"
                if (!numRanges) app.updateSetting("numRanges",[type:"number",value:3]) 
                if (numRanges) {
                    for (i=1; i <= numRanges; i++) {
                        input "range${i}", "text", title: "Range ${i}", submitOnChange:false, width: 4
                        input "range${i}FillColor", "text", title: "Fill color showing progress in Range ${i}", submitOnChange:false, width: 4
                        input "range${i}TrackColor", "text", title: "Track color between min and max of Range ${i}", submitOnChange:false, width: 4
                    }
                }   
            }
            else {
                input "staticProgressFillColor", "text", title: "Static Progress Bar Color", submitOnChange:false, width: 6
                input "staticProgressTrackColor", "text", title: "Static Progress Track Color", submitOnChange:false, width: 6
            }
        }
        section(getFormat("header-green", "${getImage("Blank")}"+" Range Label Configuration")) {
            input "showProgressRangeLabels", "bool", title: "Show Range Labels?", defaultValue:true, submitOnChange:true, width: 12
            if (showProgressRangeLabels == true) {
                input "progressRangeLabelType", "enum", options: ["value" : "As Value", "percentage" : "As Percentage Progress"], title: "Select Label Type", defaultValue:"As Value", submitOnChange:true, width: 4
                if (progressRangeLabelType == "value") {
                    input "progressRangeDecimalPlaces", "number", title: "Decimal Places", submitOnChange: false, width: 4
                    input "progressRangeValueUnits", "text", title: "Value Units Suffix", submitOnChange: false, width: 4
                    input "progressRangeDurationLabel", "bool", title: "Display as Duration?", defaultValue:false, submitOnChange:true, width: 12
                    if (progressRangeDurationLabel) {
                        input "progressRangeValueTimeUnits", "enum", title: "Select Attribute Value Time Units", options: ["minutes", "seconds"], submitOnChange: false, width: 12
                        input "progressRangeShowHourTimeUnits", "bool", title: "Show Hours if > 0?", submitOnChange: false, width: 4
                        input "progressRangeShowMinTimeUnits", "bool", title: "Show Minutes if > 0?", submitOnChange: false, width: 4
                        input "progressRangeShowSecTimeUnits", "bool", title: "Show Seconds if > 0?", submitOnChange: false, width: 4
                    }
                }
                input "progressRangeLabelPosition", "enum", title: "Range Label Position", options: ["start" : "Inside", "end" : "Outside"], defaultValue:"Inside", submitOnChange:false, width: 6
                input "progressRangeLabelPositionOffset", "text", title: "Position Offset (number)", defaultValue:0, width: 6
                input "progressRangeLabelSize", "number", title: "Range Label Size (number)", width: 3
                input "progressRangeLabelColor", "text", title: "Range Label Color", width: 3
                input "progressRangeBorderColor", "text", title: "Range Border Color", submitOnChange: false, width: 3
                input "progressRangeMaxLabel", "bool", title: "Show Radial Label for Max Value?", submitOnChange: false, width: 3
                input "progressDataBorderColor", "text", title: "Radial Data Border Color", submitOnChange: false, width: 6
                input "progressDataLabel", "bool", title: "Show Radial Label for Data?", submitOnChange: false, width: 6
            }
        }
        section(getFormat("header-green", "${getImage("Blank")}"+" Center Label(s) Configuration")) {
            paragraph "<small>The Center of the Radial Progress Gauge can include multiple rows of labels, each of which can include static text, dynamic data associated with the same device, or dynamic data associated with a different device.</small>"
            input "numCenterLabelRows", "number", title: "Number of Rows of Labels in Chart Center", width: 4, submitOnChange: true
            if (numCenterLabelRows && numCenterLabelRows > 0) {
                for (m=1; m <= numCenterLabelRows; m++) {
                    radialProgressGaugelabelInput(m)
                }
            }
        }
    }
}

def comparisonDataChartConfig() {
    if (gType == "doughnut" || gType == "pie") {
        section(getFormat("header-green", "${getImage("Blank")}"+" Doughnut Chart Configuration")) {
            if (gType == "doughnut") {
                input "centerPercentage", "number", title: "Center Size (Percentage)", width: 4
                input "circumference", "decimal", title: "Circumference (* pi)", width: 4
                input "rotation", "decimal", title: "Rotation (* pi)", width: 4
            }
        }
        section(getFormat("header-green", "${getImage("Blank")}"+" Chart Data Configuration")) {
            deviceInput(true, true, true, false)    
        }
        section(getFormat("header-green", "${getImage("Blank")}"+" Data Label Configuration")) {
            input "hideDataLabel", "bool", title: "Hide Data Label?", defaultValue:false, submitOnChange:true, width: 12
            if (!hideDataLabel) {
                input "valueUnitsSuffix", "bool", title: "Add Value Units Suffix to Data Labels?", defaultValue:false, submitOnChange:true, width: 6
                if (valueUnitsSuffix) input "valueUnits", "text", title: "Value Units Suffix", submitOnChange: false, width: 6
                input "durationLabel", "bool", title: "Display as Duration?", defaultValue:false, submitOnChange:true, width: 12
                if (durationLabel) {
                    input "attributeValueTimeUnits", "enum", title: "Select Attribute Value Time Units", options: ["minutes", "seconds"], submitOnChange: false, width: 12
                    input "showHourTimeUnits", "bool", title: "Show Hours if > 0?", submitOnChange: false, width: 4
                    input "showMinTimeUnits", "bool", title: "Show Minutes if > 0?", submitOnChange: false, width: 4
                    input "showSecTimeUnits", "bool", title: "Show Seconds if > 0?", submitOnChange: false, width: 4
                }
                input "percentageLabel", "bool", title: "Display as Percentage?", defaultValue:false, submitOnChange:true, width: 12
                input "addPercentageSubLabel", "bool", title: "Add Percentage Sublabel?", defaultValue:false, submitOnChange:true, width: 12
                if (addPercentageSubLabel) input "percentPosition", "enum", title: "Percent Position", options: ["end" : "Outside Circle", "bottom" : "Under Value"], submitOnChange: false, width: 4
                if (gType == "doughnut") input "dataLabelPosition", "enum", title: "Data Label Position", options: ["Inside", "Outside"], defaultValue:"Inside", submitOnChange:false, width: 6
                input "dataLabelPositionOffset", "text", title: "Position Offset (number)", defaultValue:0, width: 6
            }
            customStateInput()
        }
        if (gType == "doughnut") {
            section(getFormat("header-green", "${getImage("Blank")}"+" Center Label(s) Configuration")) {
                paragraph "<small>The Center of the Chart can include multiple rows of labels, each of which can include static text, dynamic data associated with the same device, or dynamic data associated with a different device.</small>"
                input "numCenterLabelRows", "number", title: "Number of Rows of Labels in Chart Center", width: 4, submitOnChange: true
                if (numCenterLabelRows && numCenterLabelRows > 0) {
                    for (k=1; k <= numCenterLabelRows; k++) {
                        labelInput(k)
                    }
                }
            }
        }
    }
    
}

def labelInput(labelID) {
    paragraph "<div style='color:#000000;font-weight: bold;background-color:#ededed;border: 1px solid;box-shadow: 2px 3px #A9A9A9'> <b>Configuration for Center Label Row ${labelID}</b></div>"
    input labelID + "LabelType", "enum", title: "Row " + labelID + " Label Type", options: ["none" : "None", "title" : "Chart Title", "sum" : "Data Sum", "percentage" : "Data Percentage of Total", "attribute" : "Device Attribute Value"], defaultValue: "None", submitOnChange: true, width: 12
    if (settings[labelID + "LabelType"] && settings[labelID + "LabelType"] != "none") {
        input labelID + "LabelSize", "number", title: "Row " + labelID + " Label Text Size", width: 4
        input labelID + "StaticLabelColor", "text", title: ("Row " + labelID + " Static Label Color"), width: 4
        if (settings[labelID + "LabelType"] != "title") {
            if (settings[labelID + "LabelType"] == "attribute") deviceInput(false, false, false, true, labelID)
            else if (settings[labelID + "LabelType"] == "percentage") input labelID + "PercentageDataAttributes", "enum", title: "Combined Percentage of Which Attribute(s)?", options: settings["theAtt"], multiple:true, submitOnChange:false, required: true
            else if (settings[labelID + "LabelType"] == "sum") input labelID + "SumDataAttributes", "enum", title: "Sum of Which Attribute(s)?", options: settings["theAtt"], multiple:true, submitOnChange:false, required: true
            if (settings[labelID + "LabelType"] != "percentage") {
                input labelID + "LabelPrefix", "text", title: "Row " + labelID + " Label Prefix", submitOnChange: false, width: 4
                input labelID + "LabelSuffix", "text", title: "Row " + labelID + " Label Suffix", submitOnChange: false, width: 4
                input labelID + "DurationLabel", "bool", title: "Display Row " + labelID + " Label as Duration?", defaultValue:false, submitOnChange:true, width: 12
                if (settings[labelID + "DurationLabel"]) {
                    input labelID + "ValueTimeUnits", "enum", title: "Select Row " + labelID + " Value Time Units", options: ["minutes", "seconds"], submitOnChange: false, width: 12
                    input "showHourTimeUnits" + labelID, "bool", title: "Show Hours if > 0?", submitOnChange: false, width: 4, defaultValue: true
                    input "showMinTimeUnits" + labelID, "bool", title: "Show Minutes if > 0?", submitOnChange: false, width: 4, defaultValue: true
                    input "showSecTimeUnits" + labelID, "bool", title: "Show Seconds if > 0?", submitOnChange: false, width: 4, defaultValue: false
                }
            }
            input labelID + "DynamicLabelColor", "bool", title: "Configure Dynamic Label Color for Row " + labelID + "?", defaultValue:false, submitOnChange:true, width: 12
            if (settings[labelID + "DynamicLabelColor"]) {
                def colorRuleOptions = ["Independent" : "Independent"]
                for (j=1; j <= numCenterLabelRows; j++) {
                    if (j != labelID) colorRuleOptions << ["${j}" : "Follows Row ${j}"]
                }
                input labelID + "DynamicLabelColorType", "enum", title: "Rules For Row " + labelID + " Label Dynamic Color", options: colorRuleOptions, defaultValue: "Independent", submitOnChange: true, width: 6
                if (settings[labelID + "DynamicLabelColorType"] == "Independent") {
                    input labelID + "DynamicLabelNumStates", "number", title: "How many states?", defaultValue:2, submitOnChange:true, width: 6
                    paragraph "<small> States can be defined as a single text value, a single numeric value, or a range of numeric values. Define a range of numeric values as MIN:MAX (example: 1:50). Ranges are inclusive of both MIN and MAX. </small>"
                    if (!settings[labelID + "DynamicLabelNumStates"]) app.updateSetting(labelID + "DynamicLabelNumStates",[type:"number",value:2]) 
                    if (settings[labelID + "DynamicLabelNumStates"]) {
                        for (i=1; i <= settings[labelID + "DynamicLabelNumStates"]; i++) {
                            input labelID + "LabelState${i}", "text", title: "State ${i}", submitOnChange:false, width: 6
                            input labelID + "LabelState${i}Color", "text", title: "Color", submitOnChange:false, width: 6
                        }
                    } 
                }
            }
        }
    }
}

def radialProgressGaugelabelInput(labelID) {
    paragraph "<div style='color:#000000;font-weight: bold;background-color:#ededed;border: 1px solid;box-shadow: 2px 3px #A9A9A9'> <b>Configuration for Center Label Row ${labelID}</b></div>"
    input labelID + "LabelType", "enum", title: "Row " + labelID + " Label Type", options: ["none" : "None", "title" : "Chart Title", "value" : "Data Value", "percentage" : "Percentage Progress", "attribute" : "Device Attribute Value"], defaultValue: "None", submitOnChange: true, width: 12
    if (settings[labelID + "LabelType"] && settings[labelID + "LabelType"] != "none") {
        def inputWidth = (settings[labelID + "LabelColorSetting"] == "static") ? 4 : 6
        input labelID + "LabelSize", "number", title: "Row " + labelID + " Label Text Size", width: inputWidth
        input labelID + "LabelColorSetting", "enum", options: ["static" : "Static Color", "follow" : "Follow Range Color"], title: ("Row " + labelID + " Label Color Setting"), width: inputWidth, submitOnChange: true
        if (settings[labelID + "LabelColorSetting"] == "static") input labelID + "StaticLabelColor", "text", title: ("Row " + labelID + " Static Label Color"), width: inputWidth

        if (settings[labelID + "LabelType"] != "title") {
            if (settings[labelID + "LabelType"] == "attribute") deviceInput(false, false, false, true, labelID)
            if (settings[labelID + "LabelType"] == "value" || settings[labelID + "LabelType"] == "attribute") {
                input labelID + "LabelPrefix", "text", title: "Row " + labelID + " Label Prefix", submitOnChange: false, width: 6
                input labelID + "LabelSuffix", "text", title: "Row " + labelID + " Label Suffix", submitOnChange: false, width: 6
                input labelID + "DurationLabel", "bool", title: "Display Row " + labelID + " Label as Duration?", defaultValue:false, submitOnChange:true, width: 12
                if (settings[labelID + "DurationLabel"]) {
                    input labelID + "ValueTimeUnits", "enum", title: "Select Row " + labelID + " Value Time Units", options: ["minutes", "seconds"], submitOnChange: false, width: 12
                    input "showHourTimeUnits" + labelID, "bool", title: "Show Hours if > 0?", submitOnChange: false, width: 4, defaultValue: true
                    input "showMinTimeUnits" + labelID, "bool", title: "Show Minutes if > 0?", submitOnChange: false, width: 4, defaultValue: true
                    input "showSecTimeUnits" + labelID, "bool", title: "Show Seconds if > 0?", submitOnChange: false, width: 4, defaultValue: false
                }
            }
        }
    }
}

def deviceInput(multipleDevices = false, multipleAttributes = false, onlyOneMultiplicityDimension = false, nonNumberAttributeAllowed = true, inputIndex = "") {
    if (multipleDevices && multipleAttributes && onlyOneMultiplicityDimension) paragraph "Select multiple devices with the same attribute (to chart values of the attribute across the devices) or a single device with multiple attributes (to chart the values of the device's attributes)."

    def deviceInputTitle = multipleDevices ? "Select the Device(s)" : "Select the Device"
    def deviceInputName = "theDevice" + inputIndex
    def attInputName = "theAtt" + inputIndex
    input deviceInputName, "capability.*", title: deviceInputTitle, multiple:multipleDevices, submitOnChange:true, required: true, width: 12
    if (settings[deviceInputName]) {
        def labelOptions = []
        def allAttrs = []
        def attTypes = [:]
        settings[deviceInputName].each { dev ->
            labelOptions << dev.displayName
            def attributes = dev.supportedAttributes
            attributes.each { att ->
                def aType = att.getDataType().toLowerCase()
                attTypes[att.name] = aType
                if (nonNumberAttributeAllowed == false && aType == "number") {
                    allAttrs << att.name
                }
                else if (nonNumberAttributeAllowed == true) {
                    allAttrs << att.name
                }
            }
        }
        if(logEnable) log.debug "Detected attribute types: ${allAttrs}"
        def devAtt = allAttrs.unique().sort()

        def attributeReqText = nonNumberAttributeAllowed ? "Attribute(s) must be either all numbers or all non-numbers." : "Attribute(s) must be numbers."
        def attributeTitle = multipleAttributes ? "Select the Attribute(s)<br><small>" + attributeReqText + "</small>" : "Select the Attribute"
        input attInputName, "enum", title: attributeTitle, options: devAtt, multiple:multipleAttributes, submitOnChange:true, required: true
           
        def anyNonNumber = false
        def anyNumber = false
        if (multipleAttributes == false && settings[attInputName]) {
            def theType = attTypes[settings[attInputName]]
            if (theType && theType == "number") anyNumber = true
            else anyNonNumber = true
        }
        else if (multipleAttributes == true && settings[attInputName] && settings[attInputName].size() > 1) {
            settings[attInputName].each { attName ->                        
                def theType = attTypes[attName]
                if (theType && theType == "number") anyNumber = true
                else anyNonNumber = true
            }         
        }           
        if(logEnable) log.debug "Detected attribute type: ${attTypes}"
        if (anyNumber && anyNonNumber) paragraph "*Warning: Selected attributes are not all numbers or all non-numbers as required*"
        if (nonNumberAttributeAllowed == false && anyNonNumber == true) paragraph "*Warning: Not all selected attributes are non-numbers as required*"
        else if (anyNumber && !anyNonNumber) state.isNumericalData = true
        else if (!anyNumber && anyNonNumber) state.isNumericalData = false

        if (inputIndex == "") dataType = "rawdata"
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
    if(!state.accessToken) createAccessToken()
    checkEnableHandler()
    if(pauseApp) {
        log.info "${app.label} is Paused"
    } else {
        if(updateTime == "realTime" && settings["theDevice"] && settings["theAtt"]) {
            def isDeviceCollection = settings["theDevice"] instanceof Collection
            def isAttributeCollection = settings["theAtt"] instanceof Collection
            if (isDeviceCollection && isAttributeCollection) {
                if (logEnable) log.debug "Both device and attribute are collections"
                settings["theDevice"].each { td ->
                    settings["theAtt"].each { ta ->
                        subscribe(td, ta, getEventsHandler)
                    }
                }
            }
            else if (!isDeviceCollection && isAttributeCollection) {
                if (logEnable) log.debug "Only device is a collection"
                td = settings["theDevice"]
                settings["theAtt"].each { ta ->
                    subscribe(td, ta, getEventsHandler)
                }
            }
             else if (!isDeviceCollection && !isAttributeCollection) {
                if (logEnable) log.debug "Only attribute is a collection"
                td = settings["theDevice"]
                ta = settings["theAtt"]
                subscribe(td, ta, getEventsHandler)
            }         
            else log.warn "No events subscribed to. isDeviceCollection = ${isDeviceCollection}. isAttributeCollection = ${isAttributeCollection}"  
        } else if(updateTime == "5min") {
            runEvery5Minutes(getEventsHandler)
        } else if(updateTime == "10min") {
            runEvery10Minutes(getEventsHandler) 
        } else if(updateTime == "15min") {
            runEvery15Minutes(getEventsHandler)
        } else if(updateTime == "30min") {
            runEvery30Minutes(getEventsHandler)
        } else if(updateTime == "1hour") {
            runEvery1Hour(getEventsHandler)
        } else if(updateTime == "3hour") {
            runEvery3Hours(getEventsHandler)
        } else if(updateTime == "attribute") {
            subscribe(updateDevice, updateAttribute, updateAttributeHandler)
        }
    }
} 

def updateAttributeHandler(evt) {
    if(logEnable) log.debug "In updateAttributeHandler with evt ${evt}"
    if (updateAttributeCondition == "changes" || (updateAttributeCondition == "value" && evt.value == updateAttributeValue)) runIn(10,getEvents) // give time for any data file based on the same attribute to be written and updated
}

def getEventsHandler(evt) {
    getEvents()
}

def getEvents() {
    checkEnableHandler()
    if(pauseApp || state.eSwitch) {
        log.info "${app.label} is Paused or Disabled"
    } else {
        if(logEnable) log.debug "----------------------------------------------- Start Quick Chart -----------------------------------------------"
        if (getChartConfigType(gType) == "pointData") {
            if (settings["theDevice"] && settings["theAtt"]) {
                def eventMap = [:]
                def theKey = "${settings['theDevice']};${settings['theAtt'].capitalize()}"
                def dataPoint = settings["theDevice"].currentValue(settings["theAtt"])
                def dataMap =[]
                dataMap << [date:new Date(),value:dataPoint]
                eventMap.put(theKey, dataMap)
                eventChartingHandler(eventMap)
            }
        }
        else if (getChartConfigType(gType) == "comparisonData") {
            def eventMap = [:]
            settings["theDevice"].each { theD ->
                settings["theAtt"].each { att ->
                    theKey = "${theD};${att.capitalize()}"
                    def dataPoint = theD.currentValue(att)
                    def dataMap =[]
                    dataMap << [date:new Date(),value:dataPoint]
                    eventMap.put(theKey, dataMap)
                }
            }
            eventChartingHandler(eventMap)
        }
        else if(dataSource) {
            if(logEnable) log.debug "In getEvents (${state.version}) - Event History"
            events = []
            def today = new Date().clearTime()
            if(theDays) {
                if(theDays == "999") {
                    days = today
                } else {
                    days = today - theDays.toInteger()
                }
                if (gType == "stateTiming" || state.isNumericalData == false) days = days - 1 // if building state timing chart, retrieve states for the previous day as well, so that can determine the state at 12:00:00 AM on the first day of the chart

                if(logEnable) log.debug "In getEvents - settings['theDevice']: ${settings['theDevice']} - ${settings['theAtt']} - ${days} (${theDays})"
                if(settings["theDevice"] && settings["theAtt"]) {
                    eventMap = [:]
                    settings["theDevice"].each { theD ->
                        settings["theAtt"].each { att ->
                            if(theD.hasAttribute(att)) {
                                if(reverseMap || gType == "stateTiming") {  // force reverseMap for stateTiming
                                    events = theD.statesSince(att, days, [max: 2000]).collect{[ date:it.date, value:it.value]}.flatten().reverse()
                                } else {
                                    events = theD.statesSince(att, days, [max: 2000]).collect{[ date:it.date, value:it.value]}.flatten()
                                }
                                if(logEnable) log.debug "In getEvents - events: $events for device: ${theD} - ${att}"
                                def eventsForMap = events
                                if (gType == "stateTiming" || state.isNumericalData == false) {
                                    def chartDate = days + 1
                                    if(logEnable) log.debug "In getEvents - splitting events based on the first day of the chart being $chartDate"
                                    def groupedEvents = events.groupBy{ new Date(it.date.getTime()) < chartDate}
                                    if(logEnable) log.debug "In getEvents - groupedEvents: $groupedEvents"
                                    def preEvents = groupedEvents[true]  // all events before the first day of the chart (days + 1), for use in determining the state at 12:00:00 AM on the first date
                                    def postEvents = groupedEvents[false] // all events after the first day of the chart (days + 1)
                                    if(logEnable) log.debug "In getEvents - preEvents: $preEvents postEvents: $postEvents"
                                    if (preEvents != null && preEvents.size() > 0) {
                                        def latestPreEvent = preEvents.pop()
                                        if(logEnable) log.debug "In getEvents - latestPreEvent: $latestPreEvent"
                                        latestPreEvent.date = chartDate.format("yyyy-MM-dd HH:mm:ss.SSS") // redefine the date as being 12:00:00 AM on the first day of the chart
                                        if(logEnable) log.debug "In getEvents - latestPreEvent after date change: $latestPreEvent"
                                        if (postEvents != null) postEvents.add(0,latestPreEvent)
                                        if(logEnable) log.debug "In getEvents - postEvents after adding latest PreEvent: $postEvents"
                                    }
                                    else if(logEnable) log.debug "In getEvents - no event found for the previous day; unable to determine state at start of the first day of the chart"
                                    if (postEvents != null && postEvents.size() > 0) eventsForMap = postEvents
                                }
                                
                                theKey = "${theD};${att.capitalize()}"
                                if (eventsForMap != null && eventsForMap != []) eventMap.put(theKey,eventsForMap)
                            }
                        }
                    }
                    if(logEnable) log.debug "In getEvents - eventMap: $eventMap"
                }
            }
            if(eventMap) {eventChartingHandler(eventMap) }
        } else {
            if(logEnable) log.debug "In getEvents (${state.version}) - Data File: ${fName}"
            readFile(fName)
            if(eventMap) {
                if(dataType == "rawdata") { 
                  //  if(logEnable) log.debug "In getEvents - eventMap before sorting: ${eventMap}"
                    def sortedMap = [:]
                    if(reverseMap || gType == "stateTiming") {
                        eventMap.each { attribute, attributeEvents ->
                            sortedMap[attribute] = attributeEvents.sort { a, b -> Date.parse("yyyy-MM-dd HH:mm:ss.SSS", a.date) <=> Date.parse("yyyy-MM-dd HH:mm:ss.SSS", b.date) }
                        }
                     } else {
                         eventMap.each { attribute, attributeEvents ->
                            sortedMap[attribute] = attributeEvents.sort { a, b -> Date.parse("yyyy-MM-dd HH:mm:ss.SSS", b.date) <=> Date.parse("yyyy-MM-dd HH:mm:ss.SSS", a.date) }
                        }
                     }    
                  //  if(logEnable) log.debug "In getEvents - eventMap after sorting: ${sortedMap}"
                    eventChartingHandler(sortedMap)
                }
                else if(theDays == "999") {
                    // don't have to do anymore processesing
                    eventChartingHandler(eventMap)
                } 
                else {
                    dailyMap = [:]
                    eventMap.each { it ->  
                        theKey = it.key
                        (theDev,theAttr) = theKey.split(";")
                        theD = it.value
                        if(logEnable) log.debug "In getEvents - eventMap - theKey: ${theKey} --- theD: ${theD}"
                        dailyList = []; hourlyList = []; sunList = []; monList = []; tueList = []; wedList = []; thuList = []; friList = []; satList = []
                        
                        total = 0
                        try{
                            theD.each { tdata ->                                
                                theDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", tdata.date)
                                tDay = theDate.format("EEE")
                                tDay3 = theDate.format("yyyy-MM-dd")
                                
                                if(decimals == "None") {
                                    tValue = new BigDecimal(tdata.value).setScale(0, java.math.RoundingMode.HALF_UP)
                                } else if(decimals == "1") {
                                    tValue = new BigDecimal(tdata.value).setScale(1, java.math.RoundingMode.HALF_UP)
                                } else if(decimals == "2") {
                                    tValue = new BigDecimal(tdata.value).setScale(2, java.math.RoundingMode.HALF_UP)
                                }

                                if(tDay == "Sun") {
                                    sunList << [date:tDay3,value:tValue]
                                    sunDate = tDay3
                                } else if(tDay == "Mon") {
                                    monList << [date:tDay3,value:tValue]
                                    monDate = tDay3
                                } else if(tDay == "Tue") {
                                    tueList << [date:tDay3,value:tValue]
                                    tueDate = tDay3
                                } else if(tDay == "Wed") {
                                    wedList << [date:tDay3,value:tValue]
                                    wedDate = tDay3
                                } else if(tDay == "Thu") {
                                    thuList << [date:tDay3,value:tValue]
                                    thuDate = tDay3
                                } else if(tDay == "Fri") {
                                    friList << [date:tDay3,value:tValue]
                                    friDate = tDay3
                                } else if(tDay == "Sat") {
                                    satList << [date:tDay3,value:tValue]
                                    satDate = tDay3
                                }
                            } 
                        } catch(e) {
                            log.warn "In getEvents - Something went wrong"
                            log.warn "In getEvents - tdata: ${tdata}"
                            log.error(getExceptionMessageWithLine(e))
                        }
                        sunTotal = 0; monTotal = 0; tueTotal = 0; wedTotal = 0; thuTotal = 0; friTotal = 0; satTotal = 0
                        
                        if(sunList) { sunTotal = sunList.value.sum() }
                        if(monList) { monTotal = monList.value.sum() }
                        if(tueList) { tueTotal = tueList.value.sum() }
                        if(wedList) { wedTotal = wedList.value.sum() }
                        if(thuList) { thuTotal = thuList.value.sum() }
                        if(friList) { friTotal = friList.value.sum() }
                        if(satList) { satTotal = satList.value.sum() }

                        if(sunList) dailyList << [date:sunDate,value:sunTotal]
                        if(monList) dailyList << [date:monDate,value:monTotal]
                        if(tueList) dailyList << [date:tueDate,value:tueTotal]
                        if(wedList) dailyList << [date:wedDate,value:wedTotal]
                        if(thuList) dailyList << [date:thuDate,value:thuTotal]
                        if(friList) dailyList << [date:friDate,value:friTotal]
                        if(satList) dailyList << [date:satDate,value:satTotal]

                        if(reverseMap) {
                            dailyList = dailyList.sort { a, b -> Date.parse("yyyy-MM-dd", a.date) <=> Date.parse("yyyy-MM-dd", b.date) }
                        } else {
                            dailyList = dailyList.sort { a, b -> Date.parse("yyyy-MM-dd", b.date) <=> Date.parse("yyyy-MM-dd", a.date) }
                        }
                        dailyMap.put(theKey,dailyList)
                    }
                    eventChartingHandler(dailyMap)
                }    
            }
            else if(logEnable) log.debug "In getEvents (${state.version}) - No Event Map found in Data File: ${fName}"
        }
    }
}

def eventChartingHandler(eventMap) {
    if(pauseApp || state.eSwitch) {
        log.info "${app.label} is Paused or Disabled"
    } else {
        if(logEnable) log.debug "In eventChartingHandler (${state.version}) - Device Events"
        
        
        if(logEnable) log.debug "In eventChartingHandler -- Building ${gType} Chart with eventMap ${eventMap} --"
        theDataset = []           
        def chartType = gType     
        def height = null   
        def showTitleInCenter = null
        buildChart = "{type:'${chartType}'"

        if (getChartConfigType(gType) == "pointData") {

            if (gType == "radialGauge" || gType == "progressBar") {
            
                if(eventMap) {
                    eventMap.each { it ->  
                        (theDev,theAttribute) = it.key.split(";")
                        theD = it.value
                    
                        theDatasets = []
                        if(logEnable) log.debug "In eventChartingHandler - building dataset for ${theAttribute} from data: ${theD}"
                    
                        theD.each { tdata ->
                            def theDataset = "{"
                            theDataset += "data:[${tdata.value}],"
                            def color = null
                            if (customizeStates) {    
                                for (i=1; i <= numStates; i++) {
                                    def state = settings["state${i}"]
                                    def stateColor = settings["state${i}Color"]
                                    if (color == null && state.contains(":")) {  // state is a range of values
                                        def stateRangeString = state.split(":")
                                        def stateRange = []
                                        stateRange[0] = new BigDecimal(stateRangeString[0]).setScale(0, java.math.RoundingMode.HALF_UP)      
                                        stateRange[1] = new BigDecimal(stateRangeString[1]).setScale(1, java.math.RoundingMode.HALF_UP)
                                        def dataValue = new BigDecimal(tdata.value).setScale(2, java.math.RoundingMode.HALF_UP)
                                        if (stateRange[0] != null && stateRange[1] != null) {
                                            if (dataValue >= stateRange[0] && dataValue <= stateRange[1]) color = stateColor
                                        }
                                        else log.warn "In eventChartingHandler - state range ignored because contains non-numeric values. Min value parsed is ${stateRange[0]} and max value parsed is ${stateRange[1]}"
                                    }
                                    else if (color == null && state != null && state.contains(tdata.value)) color = stateColor
                                }
                            } 
                            if (gType == "radialGauge" && color == null && trackFillColor != null) color = trackFillColor
                            else if (gType == "progressBar" && color == null && barColor != null) color = barColor
                            theDataset += "backgroundColor:'" + color + "',"
                            if (hasBar(gType)) {
                                theDataset += "barThickness:'" + globalBarThickness + "',"
                                theDataset += "borderColor:'transparent',"
                            }
                            theDataset += "}"
                            theDatasets << theDataset
                        }
                    }

                    if (gType == "progressBar") {
                        def theDataset = "{"
                        theDataset += "data:[" + (maxProgress ? maxProgress : 100) + "],"
                        if (progressTrackColor != null) theDataset += "backgroundColor:'" + progressTrackColor + "',"
                        theDataset += "barThickness:'" + globalBarThickness + "',"
                        theDataset += "borderColor:'" + progressTrackColor + "',"
                        theDataset += "}"
                        theDatasets << theDataset
                        height = globalBarThickness
                    }

                    if(logEnable) log.debug "In eventChartingHandler - the datasets: ${theDatasets}"
                    buildChart += ",data:{datasets:${theDatasets}}"        

                    buildChart += ",options: {"   
                    buildChart += "fontColor:'" + labelColor + "'"
                    if (chartPadding) buildChart += ",layout: { padding: " + chartPadding + "}"
                    if (gType == "radialGauge") {   
                        if (centerImage != null && centerImage != "") buildChart += ",plugins:{backgroundImageUrl:'" + centerImage + "'}"
                        buildChart += ",domain: [" + domainMin + "," + domainMax + "]"
                        buildChart += ",trackColor:'" + trackColor + "'"
                        buildChart += ",centerPercentage:" + centerPercentage
                        buildChart += ",roundedCorners:" + roundedCorners
                        buildChart += ",elements: { arc: { borderColor: '" + (arcBorderColor ? arcBorderColor : "white") + "', borderWidth: " + (arcBorderWidth ? arcBorderWidth : 0 ) + " } }"
                        buildChart += ",centerArea:{"
                        buildChart += "text: (val) => val + '" + (valueUnits != null ? valueUnits : "") + "'"
                        if (centerFillColor != null && centerFillColor != "" && (centerImage == null || centerImage == "")) buildChart += ",backgroundColor:'" + centerFillColor + "'"
                        if (centerSubText != null && centerSubText != "") buildChart += ",subText:'" + centerSubText + "'"
                        buildChart += ",padding:0"
                        if (labelColor) buildChart += ",fontColor:'" + labelColor + "'"
                        if (labelSize) buildChart += ",fontSize:" + labelSize
                        buildChart += "}"
                    }
                    else if (gType == "progressBar") {
                        buildChart += ",plugins:{"
                        buildChart      += "roundedBars: { cornerRadius: 4, allCorners: true, }"
                        buildChart      += ",datalabels: { "
                        if (labelColor) buildChart += "color:'" + labelColor + "',"
                        if (labelSize)  buildChart += "size:" + labelSize + "',"
                        buildChart      += "formatter: (val) => { return val.toLocaleString() + ' " + (valueUnits ? valueUnits : '%') + "';},}"
                        buildChart += "}"
                    }
                    buildChart += ",title: {display: ${(theChartTitle != "" && theChartTitle != null) ? 'true' : 'false'}, text: '${theChartTitle}', fontColor: '${labelColor}'}"

                    buildChart += "}}"
                } 
            }   
            else if (gType == "radialProgressGauge") {
                if(eventMap) {
                    def theDataValue = null
                    eventMap.each { it ->  
                        (theDev,theAttribute) = it.key.split(";")
                        theD = it.value
                        theD.each { tdata ->
                            theDataValue = tdata.value
                        }
                    }
                    if (theDataValue == null) return

                    def theDataMaps = []
                    def theLabels = []
                    def theDataFillColor = null
                    if (showProgressRanges == true) {
                        def rangeList = []
                        for (i=1; i <= numRanges; i++) {
                            def rangeDef = settings["range${i}"]
                            def rangeFillColor = settings["range${i}FillColor"]
                            def rangeTrackColor = settings["range${i}TrackColor"]
                            if (rangeDef.contains(":")) {  // state is a range of values
                                def rangeString = rangeDef.split(":")
                                def rangeThresholds = []
                                rangeThresholds[0] = new BigDecimal(rangeString[0]).setScale(0, java.math.RoundingMode.HALF_UP)      
                                rangeThresholds[1] = new BigDecimal(rangeString[1]).setScale(1, java.math.RoundingMode.HALF_UP)
                                if (rangeThresholds[0] != null && rangeThresholds[1] != null) {
                                    def range = [min: rangeThresholds[0], max: rangeThresholds[1], fill: rangeFillColor, track: rangeTrackColor]
                                    rangeList.add(range)
                                }
                                else log.warn "In eventChartingHandler - state range ignored because contains non-numeric values. Min value parsed is ${rangeThresholds[0]} and max value parsed is ${rangeThresholds[1]}"
                            }
                            else log.warn "In eventChartingHandler - range value ${rangeDef} not defined properly with MIN:MAX"
                        }
                        rangeList.sort { a, b -> a.min <=> b.min } 

                        if (logEnable) log.debug "In eventChartingHandler - sorted range list is ${rangeList}"

                        def rangeMarkerColor = progressRangeBorderColor ? progressRangeBorderColor : 'white'
                        def dataMarkerColor = progressDataBorderColor ? progressDataBorderColor : 'black'
                        def rangeMarkerValue = domainMax * 0.002
                        def rangeMarkerBorderWidth = 0
                        for (r=0; r < rangeList.size(); r++) {
                            range = rangeList[r]
                            if (theDataValue >= range.min && theDataValue <= range.max) theDataFillColor = range.fill
                        }
                        for (n=0; n < rangeList.size(); n++) {
                            range = rangeList[n]
                            if (theDataValue < range.min) {
                                def rangeData = range.max - range.min - rangeMarkerValue
                                theDataMaps.add([data: rangeData, color: range.track, label: "", borderWidth: rangeMarkerBorderWidth])
                            }
                            else if (theDataValue >= range.min && theDataValue <= range.max) {
                                def valueData = theDataValue - range.min - rangeMarkerValue
                                theDataFillColor = range.fill
                                theDataMaps.add([data: valueData, color: theDataFillColor, label: "", borderWidth: rangeMarkerBorderWidth])
                                theDataMaps.add([data: rangeMarkerValue, color: dataMarkerColor, label: progressDataLabel ? valueData : "", borderWidth: rangeMarkerBorderWidth])
                                def rangeData = range.max - theDataValue - rangeMarkerValue
                                theDataMaps.add([data: rangeData, color: range.track, label: "", borderWidth: rangeMarkerBorderWidth])
                            }
                            else if (theDataValue > range.max) {
                                def rangeData = range.max - range.min - rangeMarkerValue
                                def fillColor = theDataFillColor
                                theDataMaps.add([data: rangeData, color: fillColor, label: "", borderWidth: rangeMarkerBorderWidth])
                            } 
                            def maxLabel = range.max
                            if (n == rangeList.size() - 1 && !progressRangeMaxLabel) maxLabel = ""
                            theDataMaps.add([data: rangeMarkerValue, color: rangeMarkerColor, label: maxLabel, borderWidth: rangeMarkerBorderWidth])
                        }
                        if (logEnable) log.debug "In eventChartingHandler - datamaps are ${theDataMaps}"
                    }
                    else {
                        theDataFillColor = staticProgressFillColor
                        theDataMaps.add([data: theDataValue, color: staticProgressFillColor, label: theDataValue])
                        if (domainMax) theDataMaps.add([data: (domainMax - theDataValue), color: staticProgressTrackColor, label: "", borderWidth: 0])
                        else theDataMaps.add([data: (100 - theDataValue), color: staticProgressTrackColor, label: "", borderWidth: 0])
                    }
                    
                    buildChart = "{type:'doughnut'"
                    buildChart += ",data:{"

                    def labels = theDataMaps.collect { 
                        def formattedLabel = ""
                        if (it.label != "") {
                            if (progressRangeLabelType == "percentage") formattedLabel = "" + Math.round((it.label as Float) / (domainMax as Float) * 100) + "%"
                            else if (progressRangeLabelType == "value") {
                                if (progressRangeDurationLabel) {
                                    formattedLabel = formatDuration((it.label as Float), progressRangeValueTimeUnits, progressRangeShowHourTimeUnits, progressRangeShowMinTimeUnits, progressRangeShowSecTimeUnits)
                                }
                                else {
                                  //  if (logEnable) log.debug "label = ${it.label}. Rounded label = ${(it.label as Float).round(progressRangeDecimalPlaces as Integer)}. With progressRangeDecimalPlaces = ${progressRangeDecimalPlaces} "
                                    Integer precision = (progressRangeDecimalPlaces != null) ? progressRangeDecimalPlaces as Integer : 0
                                    def roundedValue = new BigDecimal(it.label).setScale(precision, java.math.RoundingMode.HALF_UP)  
                                   // formattedLabel = (progressRangeDecimalPlaces != null) ? (it.label as Float).round(progressRangeDecimalPlaces as Integer) : (it.label as Float).round(0) 
                                    formattedLabel = roundedValue.toString()
                                    if (progressRangeValueUnits) {
                                        formattedLabel += " " + progressRangeValueUnits
                                    }
                                }
                            }
                        }
                        return "'" + formattedLabel + "'"
                    }
                    buildChart      += "labels:" + labels + ","  

                    def data = theDataMaps.collect { return it.data }
                    def backgroundColor = theDataMaps.collect { return "'" + it.color + "'" }
                    def borderWidth = theDataMaps.collect { return it.borderWidth }
                    def theDataset = "{data:" + data + ", backgroundColor:" + backgroundColor + ", borderWidth:" + borderWidth + "}"
                    def theDatasets = []
                    theDatasets << theDataset
                    buildChart      += "datasets:" + theDatasets + ","

                    buildChart += "}"        

                    buildChart += ",options: {"   
                    buildChart += "fontColor:'" + labelColor + "'"
                    if (centerPercentage) buildChart += ",cutoutPercentage:" + centerPercentage
                    if (circumference) buildChart += ",circumference:" + circumference * Math.PI
                    if (rotation) buildChart += ",rotation:" + rotation * Math.PI
                    if (chartPadding) buildChart += ",layout: { padding: " + chartPadding + "}"

                    buildChart += ",plugins:{"
                    buildChart += "datalabels:{"
                    buildChart += "display: " + showProgressRangeLabels + ","
                    if (progressRangeLabelPosition) {
                        buildChart += "anchor:'" + progressRangeLabelPosition + "',"
                        buildChart += "align:'" + progressRangeLabelPosition + "',"
                        if (progressRangeLabelPositionOffset) buildChart += "offset:" + progressRangeLabelPositionOffset + ","
                    }
                    buildChart += "formatter: (val, ctx) => { return ctx.chart.data.labels[ctx.dataIndex];},"
                    buildChart += "color:'" + progressRangeLabelColor + "',"
                    buildChart += "font: { size:" + progressRangeLabelSize + "},"
                    buildChart += "},"

                    if (numCenterLabelRows && numCenterLabelRows > 0) {
                        def labelMap = [:]
                        for (labelID=1; labelID <= numCenterLabelRows; labelID++) {
                            if (settings[labelID + "LabelType"] && settings[labelID + "LabelType"] != "none") {
                                def labelValue = null
                                String labelText = ""
                                def labelColor = null
                                
                                if (settings[labelID + "LabelType"] == "title") {
                                    showTitleInCenter = true
                                    labelText = theChartTitle
                                    if (settings[labelID + "StaticLabelColor"]) labelColor = settings[labelID + "StaticLabelColor"]
                                }
                                else if (settings[labelID + "LabelType"] == "percentage" && domainMax && domainMax > 0) {
                                    labelValue = Math.round((theDataValue / domainMax) * 100)
                                    labelText = labelValue + "%"
                                }
                                else if (settings[labelID + "LabelType"] == "value") {
                                    labelValue = theDataValue
                                    if (settings[labelID + "LabelPrefix"]) labelText += settings[labelID + "LabelPrefix"] + " "
                                    if (settings[labelID + "DurationLabel"] == true) labelText += formatDuration(labelValue, settings[labelID + "ValueTimeUnits"], settings["showHourTimeUnits" + labelID], settings["showMinTimeUnits" + labelID], settings["showSecTimeUnits" + labelID])
                                    else labelText += labelValue
                                    if (settings[labelID + "LabelSuffix"])  labelText += " " + settings[labelID + "LabelSuffix"]
                                }
                                else if (settings[labelID + "LabelType"] == "attribute") {
                                    if (settings[labelID + "LabelPrefix"]) labelText += settings[labelID + "LabelPrefix"] + " "
                                    labelValue = settings["theDevice" + labelID]?.currentValue(settings["theAtt" + labelID])
                                    if (settings[labelID + "DurationLabel"] == true) labelText += formatDuration(labelValue, settings[labelID + "ValueTimeUnits"], settings["showHourTimeUnits" + labelID], settings["showMinTimeUnits" + labelID], settings["showSecTimeUnits" + labelID])
                                    else labelText += labelValue
                                    if (settings[labelID + "LabelSuffix"])  labelText += " " + settings[labelID + "LabelSuffix"]
                                }

                                if (settings[labelID + "LabelColorSetting"] == "static" && settings[labelID + "StaticLabelColor"] != null) labelColor = settings[labelID + "StaticLabelColor"]
                                else if (settings[labelID + "LabelColorSetting"] == "follow") labelColor = theDataFillColor

                                labelMap[labelID] = [value: labelValue, text: labelText, textSize: settings[labelID + "LabelSize"], color: labelColor]
                            }
                        }
                        
                        buildChart += "doughnutlabel: {"
                        def doughnutLabels = []
                        labelMap.each { key, labelConfig ->
                            if (labelConfig.text != "") {
                                def label = ""
                                label += "{ text: '" + labelConfig.text + "',"
                                if (labelConfig.textSize) label += "font: { size: " + labelConfig.textSize + "},"
                                label += "color:'" + labelConfig.color + "',"   
                                label +=   "}"   
                                doughnutLabels << label
                            }                    
                        }
                        buildChart += "labels: ${doughnutLabels}"
                        buildChart += "}," // end doughnutlabels
                    }
                    
                    buildChart += "}"  // end plugins
                    
                    buildChart += ",legend:{display: false}"
                    buildChart += ",title: {display: " + ((theChartTitle != "" && theChartTitle != null && (showTitleInCenter == null || showTitleInCenter == false) ) ? 'true' : 'false') + ", text: '${theChartTitle}', fontColor: '${labelColor}'}"

                    buildChart += "}}"
                    if(logEnable) log.debug "builderChart = ${buildChart}"
                }  
            }        
        }
        else if (getChartConfigType(gType) == "comparisonData") {          
            if(eventMap) {
                theDatasets = []
                theData = []
                theBackgroundColor = []
                def sum = 0
                eventMap.each { it ->  
                    (theDev,theAttribute) = it.key.split(";")
                    theD = it.value
                    theD.each { tdata ->
                        theData << tdata.value
                        def dataValue = new BigDecimal(tdata.value).setScale(2, java.math.RoundingMode.HALF_UP)
                        sum += dataValue as Double
                        def color = null
                        if (customizeStates) {    
                            if (customStateCriteria == "Value") {
                                for (i=1; i <= numStates; i++) {
                                    def state = settings["state${i}"]
                                    def stateColor = settings["state${i}Color"]
                                    if (color == null && state.contains(":")) {  // state is a range of values
                                        def stateRangeString = state.split(":")
                                        def stateRange = []
                                        stateRange[0] = new BigDecimal(stateRangeString[0]).setScale(0, java.math.RoundingMode.HALF_UP)      
                                        stateRange[1] = new BigDecimal(stateRangeString[1]).setScale(1, java.math.RoundingMode.HALF_UP)
                                        if (stateRange[0] != null && stateRange[1] != null) {
                                            if (dataValue >= stateRange[0] && dataValue <= stateRange[1]) color = stateColor
                                        }
                                        else log.warn "In eventChartingHandler - state range ignored because contains non-numeric values. Min value parsed is ${stateRange[0]} and max value parsed is ${stateRange[1]}"
                                    }
                                    else if (color == null && state != null && state.contains(tdata.value)) color = stateColor
                                }
                            } 
                            else if (customStateCriteria == "Device") { 
                                def sanitizedDevice = settings["theDevice"].replaceAll("\\s","").toLowerCase()
                                def stateColor = settings["state${sanitizedDevice}Color"]
                                if (color == null && stateColor != null) color = stateColor
                            }
                            else if (customStateCriteria == "Attribute") { 
                                def sanitizedAtt = theAttribute.replaceAll("\\s","").toLowerCase()
                                def stateColor = settings["state${sanitizedAtt}Color"]
                                if (color == null && stateColor != null) color = stateColor
                            }  
                            if (color != null) theBackgroundColor << "'" + color + "'"
                        }
                    }
                }
                def theDataset = "{"
                theDataset += "data:" + theData + ","
                if (theBackgroundColor != null && theBackgroundColor.size() > 0) theDataset += "backgroundColor:" + theBackgroundColor + ","
                theDataset += "datalabels: { labels: { "

                theDataset += "name: { "
                def position = "middle"
                if (dataLabelPosition && dataLabelPosition == "Outside") position = "end"
                theDataset += "align: '" + position + "', " // start of name datalabel
                if (dataLabelPositionOffset) theDataset += "offset: " + dataLabelPositionOffset + ", " // start of name datalabel
                if (labelColor) theDataset += "color:'" + labelColor + "',"
                if (labelSize)  theDataset += "font: { size:" + labelSize + "},"
                if (durationLabel) {
                    theDataset += "formatter: function(value,context) {"
                    if (attributeValueTimeUnits == "seconds") {
                        theDataset += "var hours = Math.floor(value / 3600);"
                        theDataset += "var mins = Math.floor((value % 3600) / 60);"
                        theDataset += "var secs = Math.floor(value % 60);" 
                    }
                    else if (attributeValueTimeUnits == "minutes") {
                        theDataset += "var hours = Math.floor(value / 60);"
                        theDataset += "var mins = Math.floor((value % 60) / 60);"
                        theDataset += "var secs = 0;" 
                    }
                    theDataset += "var label = '';"
                    if (showHourTimeUnits) theDataset += "if (hours > 0) { label += hours + 'h';}"
                    if (showMinTimeUnits) theDataset += "if (mins > 0) { label += mins + 'm';}"
                    if (showSecTimeUnits) theDataset += "if (secs > 0) { label += secs + 's';}"
                    theDataset += "return label;}"
                }
                else if (percentageLabel) {
                    theDataset += "formatter: function(value,context) {"
                    theDataset += "var sum = 0;"
                    theDataset += "for (var i=0; i < context.chart.data.datasets[0].data.length; i++) {"
                    theDataset +=     "sum += context.chart.data.datasets[0].data[i];"
                    theDataset += "}"
                    theDataset += "var percent = Math.floor((value / sum)*100);"
                    theDataset += "return percent + '%';}"
                }
                else {
                    theDataset += "formatter: function(value,context) { var label = value.toLocaleString()" + ((valueUnitsSuffix && valueUnits != null) ? (" + ' " + valueUnits + '%') : "") + ";"
                    theDataset += "return label;}"
                }
                theDataset += "}" // end of name datalabel

                if (addPercentageSubLabel) {
                    theDataset += ",value: {"
                    theDataset += "align:'" + (percentPosition ? percentPosition : 'bottom') + "',"
                    if (percentPosition == "end") theDataset += "anchor: 'end',"
                    if (labelColor) theDataset += "color:'" + labelColor + "',"
                    if (labelSize)  theDataset += "font: { size:" + labelSize + "},"
                    theDataset += "formatter: function(value,context) {"
                    theDataset += "var sum = 0;"
                    theDataset += "for (var i=0; i < context.chart.data.datasets[0].data.length; i++) {"
                    theDataset +=     "sum += context.chart.data.datasets[0].data[i];"
                    theDataset += "}"
                    theDataset += "var percent = Math.floor((value / sum)*100);"
                    theDataset += "return '(' + percent + '%)';}"
                    theDataset += "}" // end of value datalabel
                }
                theDataset += "}}" // end of labels and datalabels
                theDataset += "}" // end of dataset
                theDatasets << theDataset

                if(logEnable) log.debug "In eventChartingHandler - the datasets: ${theDatasets}"
                buildChart += ",data:{datasets:" + theDatasets + "}"        

                buildChart += ",options: {"   
                buildChart += "fontColor:'" + labelColor + "'"
                if (centerPercentage) buildChart += ",cutoutPercentage:" + centerPercentage
                if (circumference) buildChart += ",circumference:" + circumference * Math.PI
                if (rotation) buildChart += ",rotation:" + rotation * Math.PI
                if (chartPadding) buildChart += ",layout: { padding: " + chartPadding + ",}"

                buildChart += ",plugins:{"
                
                if (hideDataLabel) {
                    buildChart      += "datalabels: { display: false},"
                }
                if (numCenterLabelRows && numCenterLabelRows > 0) {
                    def labelMap = [:]
                    for (labelID=1; labelID <= numCenterLabelRows; labelID++) {
                        if (settings[labelID + "LabelType"] && settings[labelID + "LabelType"] != "none") {
                            def labelValue = null
                            def labelText = ""
                            def labelColor = null
                            
                            if (settings[labelID + "LabelType"] == "title") {
                                showTitleInCenter = true
                                labelText = theChartTitle
                                if (settings[labelID + "StaticLabelColor"]) labelColor = settings[labelID + "StaticLabelColor"]
                            }
                            else if (settings[labelID + "LabelType"] == "percentage" || settings[labelID + "LabelType"] == "sum") {
                                def partialSum = 0
                                def total = 0
                                eventMap.each { it ->  
                                    (theDev,theAttribute) = it.key.split(";")
                                    theD = it.value
                                    theD.each { tdata ->
                                        if (settings[labelID + "LabelType"] == "percentage" && settings[labelID + "PercentageDataAttributes"].collect{it.capitalize()}.contains(theAttribute)) partialSum += tdata.value
                                        else if (settings[labelID + "LabelType"] == "sum" && settings[labelID + "SumDataAttributes"].collect{it.capitalize()}.contains(theAttribute)) partialSum += tdata.value
                                        total += tdata.value
                                    }
                                }
                                if (settings[labelID + "LabelType"] == "percentage") {
                                    labelValue = Math.round((partialSum / total) * 100)
                                    labelText = labelValue + "%"
                                }
                                else if (settings[labelID + "LabelType"] == "sum") {
                                    labelValue = partialSum
                                    if (settings[labelID + "LabelPrefix"]) labelText += settings[labelID + "LabelPrefix"] + " "
                                    if (settings[labelID + "DurationLabel"] == true) labelText += formatDuration(labelValue, settings[labelID + "ValueTimeUnits"], settings["showHourTimeUnits" + labelID], settings["showMinTimeUnits" + labelID], settings["showSecTimeUnits" + labelID])
                                    else labelText += labelValue
                                    if (settings[labelID + "LabelSuffix"])  labelText += " " + settings[labelID + "LabelSuffix"]
                                }
                            }
                            else if (settings[labelID + "LabelType"] == "attribute") {
                                if (settings[labelID + "LabelPrefix"]) labelText += settings[labelID + "LabelPrefix"] + " "
                                labelValue = settings["theDevice" + labelID]?.currentValue(settings["theAtt" + labelID])
                                if (settings[labelID + "DurationLabel"] == true) labelText += formatDuration(labelValue, settings[labelID + "ValueTimeUnits"], settings["showHourTimeUnits" + labelID], settings["showMinTimeUnits" + labelID], settings["showSecTimeUnits" + labelID])
                                else labelText += labelValue
                                if (settings[labelID + "LabelSuffix"])  labelText += " " + settings[labelID + "LabelSuffix"]
                            }

                            if (!settings[labelID + "DynamicLabelColor"] && settings[labelID + "StaticLabelColor"] != null) labelColor = settings[labelID + "StaticLabelColor"]
                            else if (settings[labelID + "DynamicLabelColor"] && settings[labelID + "DynamicLabelColorType"] == "Independent") {
                                for (i=1; i <= settings[labelID + "DynamicLabelNumStates"]; i++) {
                                    def state = settings[labelID + "LabelState${i}"]
                                    def stateColor = settings[labelID + "LabelState${i}Color"]
                                    if (labelColor == null && state.contains(":")) {  // state is a range of values
                                        def stateRangeString = state.split(":")
                                        def stateRange = []
                                        stateRange[0] = new BigDecimal(stateRangeString[0]).setScale(0, java.math.RoundingMode.HALF_UP)      
                                        stateRange[1] = new BigDecimal(stateRangeString[1]).setScale(1, java.math.RoundingMode.HALF_UP)
                                        if (stateRange[0] != null && stateRange[1] != null) {
                                            if (labelValue >= stateRange[0] && labelValue <= stateRange[1]) labelColor = stateColor
                                        }
                                    }
                                    else if (labelColor == null && state != null && state.contains(labelValue as String)) labelColor = stateColor
                                    else if (logEnable) log.debug "Label Value = ${labelValue} of class ${labelValue.class.name}. No matchiung dynamic color state for state ${state} of class ${state.class.name} with color ${stateColor}"
                                }                          
                            }
                            labelMap[labelID] = [value: labelValue, text: labelText, textSize: settings[labelID + "LabelSize"], color: labelColor, labelType: settings[labelID + "LabelType"], dynamicColorType: settings[labelID + "DynamicLabelColorType"]]
                        }
                    }
                    
                    buildChart += "doughnutlabel: {"
                    def doughnutLabels = []
                    labelMap.each { key, labelConfig ->
                        if (labelConfig.text != null) {
                            def label = ""
                            label += "{ text: '" + labelConfig.text + "',"
                            if (labelConfig.textSize) label += "font: { size: " + labelConfig.textSize + "},"
                            if ((labelConfig.labelType == "title" || labelConfig.dynamicColorType == "Independent") && labelConfig.color != null) label += "color:'" + labelConfig.color + "',"
                            else if (labelConfig.dynamicColorType != null && labelConfig.dynamicColorType != "Independent" && labelMap[labelConfig.dynamicColorType as Integer] != null && labelMap[labelConfig.dynamicColorType as Integer].color != null) {
                                    label += "color:'" + labelMap[labelConfig.dynamicColorType as Integer].color + "',"
                            }    
                            label +=   "}"   
                            doughnutLabels << label
                        }                    
                    }
                    buildChart += "labels: ${doughnutLabels}"
                    buildChart += "}," // end doughnutlabels
                }
                
                buildChart += "}"  // end plugins
                buildChart += ",title: {display: " + ((theChartTitle != "" && theChartTitle != null && (showTitleInCenter == null || showTitleInCenter == false) ) ? 'true' : 'false') + ", text: '${theChartTitle}', fontColor: '${labelColor}'}"

                buildChart += "}}"
                if(logEnable) log.debug "builderChart = ${buildChart}"
            }      
                      
        }
        else if (gType == "stateTiming" || state.isNumericalData == false)  {
            if(logEnable) log.debug "In eventChartingHandler -- Building Non-Numerical Chart --"
            theLabels = []
            theDatasets = []
            
            Date today = new Date()
            Date startOfToday = today.clearTime()
            Calendar cal = Calendar.getInstance()
            cal.setTimeZone(location.timeZone)
            cal.setTime(startOfToday + 1)
            cal.add(Calendar.SECOND, -1)
            Date endOfToday = cal.getTime()
            
            def minDate = null
            def maxDate = null
            def dayOffset = 0            
            if (theDays != "999") dayOffset = theDays.toInteger()

            
            if (xAxisOrigin == "day") {
                if(gType == "stateTiming" || reverseMap) {
                    minDate = startOfToday - dayOffset
                }
                else {
                    minDate = endOfToday
                }          
            }
            else if (gType != "stateTiming" && reverseMap == false && xAxisOrigin == "currentTime") {
                minDate = new Date()     
            }
            
            if (xAxisTerminal == "day") {
                if(gType == "stateTiming" || reverseMap) {
                    maxDate = endOfToday
                }
                else {
                    maxDate = startOfToday - dayOffset
                }          
            }
            else if (xAxisTerminal == "currentTime") {
                if(gType == "stateTiming" || reverseMap) {
                    maxDate = new Date()
                }     
                else {
                    maxDate = startOfToday - dayOffset
                }         
            }
            
            def minDateData = null
            def maxDateData = null
            
            def maxBarThickness = 0
            if (customizeStates) {
                 for (i=1; i <= numStates; i++) {
                     if (settings["state${i}BarThickness"] != null && settings["state${i}BarThickness"] > maxBarThickness) maxBarThickness = settings["state${i}BarThickness"]
                 }
            }
            else maxBarThickness = globalBarThickness
            def barThickness = maxBarThickness ? maxBarThickness : 30
            def extraChartSpacing = 20
            def legendSpace = displayLegend ? 30 : 0
            def titleSpace = (theChartTitle != "" && theChartTitle != null) ? 40 : 0
            def numAttributes = eventMap.size()
            
            def legendItems = []
            def uniqueLegendItemIndices = []
            
            chartType = gType == "stateTiming" ? "horizontalBar" : gType            
            height = legendSpace + titleSpace + (barThickness + extraChartSpacing)*numAttributes

            buildChart = "{type:'${chartType}'"
           
            if(eventMap) {
                z = 0
                n = 0
                eventMap.each { it ->  
                    (theDev,theAttribute) = it.key.split(";")
                    theD = it.value

                    if(showDevInAtt) {
                        if (ZdevFilters) {    // NOT a typo, just making it so it doesn't have access
                            filters = devFilters.split(";")
                            if(filters) {
                                x=1
                                log.trace "start: $theDev"
                                filters.each { filt ->
                                    log.trace "working on: $filt --- $x"
                                    if(x==1) {
                                        newDev = theDev.replace("${filt}", "").trim()
                                    } else {
                                        newDev = newDev.replace("${filt}", "").trim()
                                    }
                                    x += 1
                                }
                                log.trace "finished: $newDev"
                                if (showAtt == null || showAtt == true) theAttribute = "${newDev} - ${theAttribute}"
                                else theAttribute = "${newDev}"
                            }
                        } else {
                            if (showAtt == null || showAtt == true) theAttribute = "${theDev} - ${theAttribute}"
                            else theAttribute = "${theDev}"
                        }
                    } else {
                        theAttribute = "${theAttribute}"
                    }                    
                    theLabels << "'${theAttribute}'"
                    
                    theDataset = ""
                    if(logEnable) log.debug "In eventChartingHandler - building dataset for ${theAttribute} from data: ${theD}"
                    
                    y=0
                    theD.each { tdata ->
                        theDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", tdata.date.toString())
                        
                        if (maxDateData == null) maxDateData = theDate
                        else if (theDate.after(maxDateData)) maxDateData = theDate
                        if (minDateData == null) minDateData = theDate
                        else if (minDateData.after(theDate)) minDateData = theDate
                        
                        if (y < theD.size() - 1) theNextDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", theD[y+1].date.toString())
                        else {
                            // last date will either be the latest date (today) or the earliest date depending on reverseMap
                            if(gType == "stateTiming" || reverseMap) {
                                if (extrapolateDataToCurrentTime == true) theNextDate = new Date()
                                else theNextDate = maxDateData // set to max date of the data since not assuming that the last datapoint persists until the current time
                            }
                            else {
                                if (extrapolateDataToCurrentTime == true) theNextDate = minDate
                                else theNextDate = maxDateData // set to max date of the data since not assuming that the last datapoint persists until the current time                                
                            }
                        }      
                            
                        if(logEnable) log.debug "In eventChartingHandler - theDate = ${theDate} theNextDate = ${theNextDate}"
 
                        tDateStart = theDate.format("yyyy-MM-dd'T'HH:mm:ss")
                        tDateEnd = theNextDate.format("yyyy-MM-dd'T'HH:mm:ss")
                        
                        theData = []
                        for (i=0; i < z; i++) {
                            def spacer = [] 
                            theData.add(spacer)
                        }
                        if(gType == "stateTiming" || reverseMap) {
                            datelist = ["new Date('" + tDateStart + "')", "new Date('" + tDateEnd + "')"]
                        }
                        else {
                            datelist = ["new Date('" + tDateEnd + "')", "new Date('" + tDateStart + "')"]
                        }
                        
                        theData.add(datelist)
                  
                        if (y>0) theDataset += ","
                        theDataset += "{"
                        theDataset += "label:'${tdata.value}'"
                        theDataset += ",data:${theData}"

                        if(logEnable) log.debug "In eventChartingHandler - Processing legend items for ${theAtt} with z=${z}. Legend Items includes ${legendItems}"
                        if (!legendItems.contains(tdata.value)) {
                            legendItems.add(tdata.value)
                            uniqueLegendItemIndices.add(n)
                            if(logEnable) log.debug "In eventChartingHandler - adding ${tdata.value} to legend items with z=${z} y=${y}"
                        }
                        else if(logEnable) log.debug "In eventChartingHandler - Skipped adding ${tdata.value} because alreaday included in legend items"

                        def color = null
                        def bThickness = null
                        if (customizeStates) {    
                            for (i=1; i <= numStates; i++) {
                                def state = settings["state${i}"]
                                def stateColor = settings["state${i}Color"]
                                def stateBarThickness = settings["state${i}BarThickness"]
                                if (state != null && state.contains(":")) {  // state is a range of values
                                    def stateRangeString = state.split(":")
                                    def stateRange = []
                                    stateRange[0] = new BigDecimal(stateRangeString[0]).setScale(0, java.math.RoundingMode.HALF_UP)      
                                    stateRange[1] = new BigDecimal(stateRangeString[1]).setScale(1, java.math.RoundingMode.HALF_UP)
                                    def dataValue = new BigDecimal(tdata.value).setScale(2, java.math.RoundingMode.HALF_UP)
                                    if (stateRange[0] != null && stateRange[1] != null) {
                                        if (dataValue >= stateRange[0] && dataValue <= stateRange[1]) {
                                            if (color == null) color = stateColor
                                            if (bThickness == null) bThickness = stateBarThickness
                                        }
                                    }
                                    else log.warn "In eventChartingHandler - state range ignored because contains non-numeric values. Min value parsed is ${stateRange[0]} and max value parsed is ${stateRange[1]}"
                                }
                                else if (state != null && state.contains(tdata.value)) {
                                    if (color == null) color = stateColor
                                    if (bThickness == null) bThickness = stateBarThickness
                                }
                            }
                            if (color == null) {
                                if(logEnable) log.debug "In eventChartingHandler - No color found for state ${tdata.value}. Using default color"
                                color = 'black'
                            }
                            if (bThickness == null) {
                                if(logEnable) log.debug "In eventChartingHandler - No bar thickness found for state ${tdata.value}. Using default thickness"
                                bThickness = 30
                            }
                        }   
                        else {                        
                            bThickness = globalBarThickness
                            
                            def theGreenData = ["active", "open", "locked", "present", "on", "open", "true", "dry"]
                            def theRedData = ["inactive", "closed", "unlocked", "not present", "off", "closed", "false", "wet"]
                        
                            if (theGreenData.contains(tdata.value)) color = "green"
                            else if (theRedData.contains(tdata.value)) color = "red"
                        }
                        theDataset += ",barThickness: ${bThickness}"
                        theDataset += ",backgroundColor:'${color}'"
                        theDataset += ",borderColor:'${color}'"
                        theDataset += ",borderWidth:1"
                      //  theDataset += ",borderSkipped:'false'"
                        theDataset += "}"
                       
                        y++
                        n++
                    }
                    if(logEnable) log.debug "In eventChartingHandler - dataset: ${theDataset}"
                    
                    theDatasets << theDataset
                    z++
                }
                
                
                if (xAxisOrigin == "data") {
                    if(gType == "stateTiming" || reverseMap) {
                        minDate = minDateData
                    }     
                    else {
                        minDate = mxaDateData
                    }       
                }  
                
                if (xAxisTerminal == "data") {
                    if(gType == "stateTiming" || reverseMap) {
                        maxDate = maxDateData
                    }     
                    else {
                        maxDate = minDateData
                    }         
                }   
                
                if(logEnable) log.debug "In eventChartingHandler - the datasets: ${theDatasets}"
                buildChart += ",data:{labels:${theLabels},datasets:${theDatasets}}"        
                buildChart += ",options: {"     
                buildChart += "title: {display: ${(theChartTitle != "" && theChartTitle != null) ? 'true' : 'false'}, text: '${theChartTitle}'"
                if (chartPadding) buildChart += ",layout: { padding: " + chartPadding + ",}"
                if (labelColor) buildChart += ", fontColor: '${labelColor}'"
                buildChart += "}"
  
                // filter out redundant legend items
                def legendFilterLogic = ""
                
                for (i=0; i<=uniqueLegendItemIndices.size()-1; i++) {
                    legendFilterLogic += "item.datasetIndex == ${uniqueLegendItemIndices[i]}"
                    if (i < uniqueLegendItemIndices.size()-1) legendFilterLogic += " || "
                }

                buildChart += ",legend:{display: ${displayLegend}, labels: { ${legendBoxWidth != null ? "boxWidth: ${legendBoxWidth}," : ""} ${legendFontSize != null ? "fontSize: ${legendFontSize}," : ""} ${labelColor != null ? "fontColor: '${labelColor}'," : ""} filter: function(item, chartData) { return ${legendFilterLogic}}}}"
                 
                if (xAxisTimeFormat) {
                    displayFormat = xAxisTimeFormat
                }
                else {
                    if (theDays == "999") {
                        if(dFormat) {
                            displayFormat = 'H'
                        } else {
                            displayFormat = 'ha'
                        }
                    } else {
                        if(dFormat) {
                            displayFormat = 'ddd H'
                        } else {
                            displayFormat = 'ddd ha'
                        }
                    }    
                }
                
                def maxRotation = 0
                if (theDays != "999") maxRotation = 75
                
                if (onChartValueLabels)  buildChart += ",plugins: {datalabels: {anchor: 'start', clamp: true, display: 'auto', align:'end', offset: 0, color:'black', formatter: function(value,context) { return context.chart.data.datasets[context.datasetIndex].label;}}}"
                        
                // if state timing chart, force stacking Y axis and not stacking X axis
                if (gType == "stateTiming") buildChart += ",scales: {xAxes: [{display: ${displayXAxis}, stacked: false, type: 'time', time: {unit: '${xAxisTimeUnit ? xAxisTimeUnit : 'hour'}', displayFormats: {hour: '${displayFormat}'}}, ticks: {${tickSource != null ? "source:'" + tickSource + "'," : ""} fontColor: '${labelColor}', maxRotation: ${maxRotation}, ${minDate != null && maxDate != null ? "min: new Date('" + minDate.format("yyyy-MM-dd'T'HH:mm:ss").toString() + "'), max: new Date('" + maxDate.format("yyyy-MM-dd'T'HH:mm:ss").toString() + "')" : ""}}, gridLines:{display: ${displayXAxisGrid}, zeroLineColor: '${gridColor}', color: '${gridColor}', tickMarkLength: 5, drawBorder: false}}], yAxes: [{display: ${displayYAxis}, stacked: true, ticks: {fontColor: '${labelColor}'}, gridLines:{display: ${displayYAxisGrid}, zeroLineColor: '${gridColor}', color: '${gridColor}'}}]}"
                else buildChart += ",scales: {xAxes: [{display: ${displayXAxis}, stacked: ${stackXAxis}, type: 'time', time: {unit: '${xAxisTimeUnit ? xAxisTimeUnit : 'hour'}', displayFormats: {'hour': '${displayFormat}'}}, ticks: {${tickSource != null ? "source:'" + tickSource + "'," : ""} fontColor: '${labelColor}', maxRotation: ${maxRotation}, ${minDate != null && maxDate != null ? "min: new Date('" + minDate.format("yyyy-MM-dd'T'HH:mm:ss").toString() + "'), max: new Date('" + maxDate.format("yyyy-MM-dd'T'HH:mm:ss").toString() + "')" : ""}}, gridLines:{display: ${displayXAxisGrid}, zeroLineColor: '${gridColor}', color: '${gridColor}', tickMarkLength: 5, drawBorder: false}}], yAxes: [{display: ${displayYAxis}, stacked: ${stackYAxis}, ticks: {fontColor: '${labelColor}'}, gridLines:{display: ${displayYAxisGrid}, zeroLineColor: '${gridColor}', color: '${gridColor}'}}]}"
                
                buildChart += "}}"
            }            
        }
        else {
            if(logEnable) log.debug "In eventChartingHandler -- Building Numerical Chart --"

            x=1
            theLabels = []
            theData = []
            
            def dataTotal = 0
            def dataPointCount = 0
            
            
            def displayUnit = 'minute'
            def displayFormat = "hh:mm"
            if(dataType == "rawdata") {
                if(dFormat) {
                     displayFormat = "ddd HH:mm"
                 } else {
                     displayFormat = "ddd hh:mm"
                 }
             }
             else {
                 displayFormat = "ddd"
                 displayUnit = 'day'
            }
            
            if(eventMap) {
                eventMap.each { it ->  
                    (theDev,theAttribute) = it.key.split(";")
                    theD = it.value
                
                    if(showDevInAtt) {
                        if (ZdevFilters) {    // NOT a typo, just making it so it doesn't have access
                            filters = devFilters.split(";")
                            if(filters) {
                                x=1
                                log.trace "start: $theDev"
                                filters.each { filt ->
                                    log.trace "working on: $filt --- $x"
                                    if(x==1) {
                                        newDev = theDev.replace("${filt}", "").trim()
                                    } else {
                                        newDev = newDev.replace("${filt}", "").trim()
                                    }
                                    x += 1
                                }
                                log.trace "finished: $newDev"
                                if (showAtt == null || showAtt == true) theAttribute = "${newDev} - ${theAttribute}"
                                else theAttribute = "${newDev}"
                            }
                        } else {
                            if (showAtt == null || showAtt == true) theAttribute = "${theDev} - ${theAttribute}"
                            else theAttribute = "${theDev}"
                        }
                    } else {
                        theAttribute = "${theAttribute}"
                    }                                                      
                    theD.each { tdata ->        
                        if(logEnable) log.debug "In eventChartingHandler -- tdata.date = ${tdata.date.toString()} --"
                        def dateObj = null
                        if(dataSource) {
                            dateObj = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", tdata.date.toString())
                        } else {
                            if(dataType == "rawdata") {
                                if(tdata) {
                                    dateObj = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", tdata.date.toString())
                                } else {
                                    log.warn "There doesn't seem to be any data"
                                    log.warn "tdata: $tdata"
                                }
                            } else {
                                dateObj = Date.parse("yyyy-MM-dd", tdata.date.toString())      
                            }
                        }
                        def formattedDate = dateObj.format("yyyy-MM-dd'T'HH:mm:ss")
                        def tDate = "new Date('" + formattedDate + "')"
                        
                        if(state.isNumericalData) {
                            if(logEnable) log.debug "In eventChartingHandler -- tdata detected as number -- tdata: ${tdata}"
                            if(decimals == "None") {
                                tValue = new BigDecimal(tdata.value).setScale(0, java.math.RoundingMode.HALF_UP)
                            } else if(decimals == "1") {
                                tValue = new BigDecimal(tdata.value).setScale(1, java.math.RoundingMode.HALF_UP)
                            } else if(decimals == "2") {
                                tValue = new BigDecimal(tdata.value).setScale(2, java.math.RoundingMode.HALF_UP)
                            }
                            if(secMin) {
                                theT = tValue / 60
                            } else {
                                theT = tValue
                            }
                        } else {
                             if(logEnable) log.debug "In eventChartingHandler -- tdata detected as NOT a number -- tdata: ${tdata}"
                            theT = tdata.value
                        }

                        if (chartXAxisAsTime == null || chartXAxisAsTime == true) theLabels <<  tDate
                        else theLabels << "'" + tdata.date.toString() + "'"
                        theData << theT
                        if (theT != null) dataTotal += theT
                        dataPointCount++
                    }
                    if(x==1) {
                        buildChart = "{type:'${gType}',data:{datasets:[{label:'${theAttribute}',data:${theData}"
                        if ((gType == "bar" || gType == "horizontalBar" || gType == "progressBar") && barWidth != null) buildChart += ", barThickness: ${globalBarThickness}"
                        buildChart += "}"
                    } else {
                        buildChart += ",{label:'${theAttribute}',data:${theData}"
                        if ((gType == "bar" || gType == "horizontalBar" || gType == "progressBar") && barWidth != null) buildChart += ", barThickness: ${globalBarThickness}"
                        buildChart += "}"
                    }

                    x += 1
                    theData = []
                    
                    for (i=0; i < dataPointCount; i++) {
                        def spacer = null
                        theData.add(spacer)
                    }
                }
                buildChart += "], labels:${theLabels}},options: {"
                buildChart += "title: {display: ${(theChartTitle != "" && theChartTitle != null) ? 'true' : 'false'}, text: '${theChartTitle}', fontColor: '${labelColor}'}"
                if (chartPadding) buildChart += ",layout: { padding: " + chartPadding + ",}"
                buildChart += ",legend:{display: ${displayLegend}"
                if (legendBoxWidth != null || legendFontSize != null || labelColor != null) {
                    buildChart += ", labels: {"
                    if (legendBoxWidth != null) buildChart += "boxWidth:" + legendBoxWidth + ", "
                    if (legendFontSize != null) buildChart += "fontSize:" + legendFontSize + ", "
                    if (labelColor != null) buildChart += "fontColor:'" + labelColor + "'"
                    buildChart += "}"
                }
                buildChart += "}"
                
                def isStaticLineActive = (showStaticLine && staticLineValue != null) ? true : false
                def isDynamicLineActive = (showDynamicLine && ((dynamicLineSource == "Device Attribute Value" && dynamicLineDevice != null && dynamicLineAttribute != null) || dynamicLineSource == "Charted Value Average")) ? true : false
                
                if (isStaticLineActive || isDynamicLineActive) {
                    buildChart += ",annotation:{annotations: ["
                    if (isStaticLineActive) buildChart += "{type:'line', mode:'horizontal', scaleID: 'y-axis-0', value:${staticLineValue}, borderWidth: ${staticLineWidth != null ? staticLineWidth : 1}, borderColor:'${staticLineColor != null ? staticLineColor: "red"}'${staticLineLabel != null ? ", label: {enabled:true, content: '${staticLineLabel}'}" : ""}}"                                     
                    if (isStaticLineActive && isDynamicLineActive) buildChart += ","
                    if (isDynamicLineActive) {
                        def dynamicValue = null
                        if (dynamicLineSource == "Device Attribute Value" && dynamicLineDevice != null && dynamicLineAttribute != null) dynamicValue = dynamicLineDevice.currentValue(dynamicLineAttribute)
                        else if (dynamicLineSource == "Charted Value Average" && dataPointCount > 0) dynamicValue = dataTotal / dataPointCount
                        buildChart += "{type:'line', mode:'horizontal', scaleID: 'y-axis-0', value:${dynamicValue}, borderWidth: ${dynamicLineWidth != null ? dynamicLineWidth : 1}, borderColor:'${dynamicLineColor != null ? dynamicLineColor: "blue"}'${dynamicLineLabel != null ? ", label: {enabled:true, content: '${dynamicLineLabel}'}" : ""}}"
                    }
                    buildChart += "]}"                    
                }
                
                if (onChartValueLabels) buildChart += ",plugins: {datalabels: {anchor: 'center', align:'center', formatter: function(value,context) { return context.chart.data.datasets[context.datasetIndex].label;}}}"
                buildChart += ",scales: {xAxes: [{display: ${displayXAxis}, stacked: ${stackXAxis}, ${(chartXAxisAsTime == null || chartXAxisAsTime == true) ? "type: 'time'," : ""} time: {unit: '${displayUnit}', displayFormats: {${displayUnit}: '${displayFormat}'}}, ticks: {${tickSource != null ? "source:'" + tickSource + "'," : ""} fontColor: '${labelColor}'}, gridLines:{display: ${displayXAxisGrid}, zeroLineColor: '${gridColor}', color: '${gridColor}'}}], yAxes: [{display: ${displayYAxis}, stacked: ${stackYAxis}, ticks: {"
                if(yMinValue) buildChart += "min: ${yMinValue}, "
                buildChart += "fontColor: '${labelColor}'}, gridLines:{display: ${displayYAxisGrid}, zeroLineColor: '${gridColor}', color: '${gridColor}'}}]}"
                buildChart += "}}"
            }
        }
        if(logEnable) log.debug "builderChart = ${buildChart}"
        chartMap = [format: "png", backgroundColor: bkgrdColor, chart: buildChart]  
        if (chartHeight) chartMap['height'] = chartHeight 
        else if (height != null) chartMap['height'] = height              
        def chartJson = new JsonOutput().toJson(chartMap)                
        def shortURLResponse = sendJsonGetShortURL(chartJson)
        if (shortURLResponse != null && shortURLResponse.url != null) {
            if(logEnable) log.debug "Got short Quick Chart URL: ${shortURLResponse.url}"
            buildChart = "<img width='100%' src=\"" + shortURLResponse.url + "\" onclick=\"window.open(this.src)\">"
        }

        // Send Chart to Device
        if(dataDevice && buildChart) {
            theCLength = buildChart.length()
            if(logEnable) log.debug "In eventChartingHandler - Chart length: $theCLength"
            dataDevice.sendEvent(name: "chart", value: buildChart, isStateChange: true)
            dataDevice.sendEvent(name: "chartLength", value: theCLength, isStateChange: true)
        }
    }
    if(logEnable) log.debug "----------------------------------------------- End Quick Chart -----------------------------------------------"
}

def sendJsonGetShortURL(jsonBody) {
    def returnStatus = false
    def returnData = null
    def response = null
    def cmdParams = [
        uri: "https://quickchart.io/chart/create",
        headers: ["Content-Type": "application/json"],
        body: jsonBody,
        timeout: 30
    ]

    try{
        httpPost(cmdParams) { resp ->
            response = resp
        }
        if(response) {
            returnData = response?.data
            if(response?.status in [200, 201, 204]) {
                returnStatus = true
                //runIn(4, "poll", [overwrite: true])
            } else {
                log.error "Response status: ${response?.status}"
            }

        } else { return returnStatus }
    } catch(Exception e) {
        log.error "sendJsonGetShortURL Exception Error: ${e}"
    }
    return returnData
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

String getTheDevices(){
    if(logEnable) log.debug "In getTheDevices (${state.version})"
    login()
    uri = "http://${location.hub.localIP}:8080/local/${fName}"
    def params = [
        uri: uri,
        contentType: "text/html; charset=UTF-8",
        headers: [
            "Cookie": state.cookie
        ]
    ]
    try {
        dLabels = []
        httpGet(params) { resp ->
            if(resp!= null) {
                def theD = resp.getData()
                theData = theD.toString().split(", ")
                dSize = theData.size()
                if(logEnable) log.debug "In getTheDevices  - dSize: ${dSize}"               
                if(dSize == 0 || theD == "[]\n") {
                    log.trace "There is no data to process"
                } else {
                    if(logEnable) log.debug "In getTheDevices  - Found data: ${theData}"
                    theData.each { it ->
                        (theDev, theAttribute, theDate, theValue, theStatus) = it.replace("[","").replace("]","").split(";")
                        state.isNumericalData = "${theValue}".isNumber()
                        dLabels << theDev
                    }
                    if(logEnable) log.debug "In getTheDevices  - Finished collecting Data"
                    deviceLabels = dLabels.unique().sort()
                    return deviceLabels
                }   
            } else {
                if(logEnable) log.debug "In getTheDevices  - No Data"
            }     
        }
    } catch(e) {
        log.error(getExceptionMessageWithLine(e))
    }
}

def toJson(Map m) {
    return new groovy.json.JsonBuilder(m)
}

String readFile(fName){
    if(logEnable) log.debug "In readFile (${state.version}) - ${fName}"
    state.isData = false
    login()
    uri = "http://${location.hub.localIP}:8080/local/${fName}"
    def params = [
        uri: uri,
        contentType: "text/html; charset=UTF-8",
        headers: [
            "Cookie": state.cookie
        ]
    ]
    try {
        httpGet(params) { resp ->
            if(resp!= null) {
                def theD = resp.getData()
                theData = theD.toString().split(", ")
                dSize = theData.size()
                if(logEnable) log.debug "In readFile  - dSize: ${dSize} - dataType: ${dataType}"               
                if(dSize == 0 || theD == "[]\n") {
                    if(logEnable) log.debug "In readFile - There is no data to process"
                } else {                    
                    def xMax = 1
                    if(dataType == "rawdata") {
                        if(theDays != "999") xMax = theDays.toInteger()
                    }
                    else if(dataType == "duration") {
                        if(theDays == "999") xMax = 7  
                        else xMax = theDays.toInteger()
                    }
                    if(logEnable) log.debug "In readFile - Duration ***** Iterating through file with xMax: ${xMax} *****"
                    
                    newData = []
                    def today = new Date()
                    def theDate = today                    
                    for(x=1;x<=xMax;x++) {
                        String tDate = theDate.format("yyyy-MM-dd")
                        if(logEnable) log.debug "In readFile - Duration ***** Checking file for tDate: $tDate *****"
                        theData.each { el ->
                            if(logEnable) log.debug "In readFile - Checking ${el} -VS- ${tDate}"
                            if(el.toString().contains("${tDate}")) {
                                newData << el
                                state.isData = true
                            }
                        }
                        theDate = today - x
                        if(logEnable) log.debug "In readFile ----------------------------------------------------------------------------------"
                    }
 
                    if(state.isData) {
                        if(logEnable) log.debug "newData from reading file has size = ${newData.size()}. Contents: ${newData}"
                        eventMap = [:]
                        newData.each { it ->
                            (theDev, theAttribute, theDate, theValue, theStatus) = it.replace("[","").replace("]","").split(";")
                            theKey = "${theDev};${theAttribute}"
                            theValue = theValue.trim()
                            state.isNumericalData = "${theValue}".isNumber()
                            newValue = []
                            looking = eventMap.get(theKey)
                            if(looking) {
                                if(logEnable) log.debug "found theKey already; looking = ${looking}"
                                theFindings = eventMap.get(theKey)
                                theFindings.each { tf ->
                                    newValue << [date:tf.date,value:tf.value]
                                }
                                
                                newValue << [date:theDate,value:theValue]
                                if(logEnable) log.debug "updated newValue is ${newValue}"
                            } else {
                                newValue << [date:theDate,value:theValue]
                            }
                            eventMap.put(theKey, newValue)
                        }
                        if(logEnable) log.debug "eventMap from reading file is: ${eventMap}"
                        return eventMap
                    }
                }
            } else {
                if(logEnable) log.debug "In readFile - There is no data to process"
                state.isData = false
            }
        }
    } catch (e) {
        log.error(getExceptionMessageWithLine(e))
    }
}

Boolean getFileList(){
    login()
    if(logEnable) log.debug "In getFileList - Getting list of files"
    uri = "http://${location.hub.localIP}:8080/hub/fileManager/json";
    def params = [
        uri: uri,
        headers: [
            "Cookie": state.cookie,
        ]
    ]
    try {
        fileList = []
        httpGet(params) { resp ->
            if (resp != null){
                if(logEnable) log.debug "In getFileList - Found the files"
                def json = resp.data
                for (rec in json.files) {
                    fileType = rec.name[-3..-1]
                    if(fileType == "txt") {
                        fileList << rec.name
                    }
                }
            } else {
                //
            }
        }
        return fileList
    } catch (e) {
        log.error(getExceptionMessageWithLine(e))
    }
}

def formatDuration(value, valueUnits, showHours, showMinutes, showSeconds) {
    Integer hours = 0
    Integer mins = 0
    Integer secs = 0
    def duration = Math.floor(value) as Integer
    if (valueUnits == "seconds") {
        hours = Math.floor(duration / 3600)
        mins = Math.floor((duration % 3600) / 60)
        secs = Math.floor(duration % 60)
    }
    else if (valueUnits == "minutes") {
        hours = Math.floor(duration / 60)
        mins = Math.floor((duration % 60) / 60)
        secs = 0
    }
    def formattedValue = ""
    if (showHours && hours > 0) formattedValue += hours + 'h'
    if (showMinutes && mins > 0) formattedValue += mins + 'm'
    if (showSeconds && secs > 0) formattedValue += secs + 's'
    return formattedValue
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
