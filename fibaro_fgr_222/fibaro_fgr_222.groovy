/**
 * Device Handler for Fibaro FGR-222
 *
 * MIT License
 *
 * Copyright (c) 2017 Julien
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

metadata {
    definition (name: "Fibaro FGR-222", namespace: "julienbachmann", author: "Julien Bachmann") {
        capability "Sensor"
        capability "Actuator"

        capability "Switch"
        capability "Switch Level"
        capability "Window Shade"

        capability "Polling"
        capability "Power Meter"
        capability "Energy Meter"
        capability "Refresh"
        capability "Configuration"

        attribute "syncStatus", "enum", ["syncing", "synced"]

        command "sync"
        command "stop"

        fingerprint inClusters: "0x26,0x32"
    }

    tiles(scale: 2) {
        multiAttributeTile(name:"mainTitle", type:"generic", width:6, height:4, canChangeIcon: true) {
            tileAttribute("device.windowShade", key: "PRIMARY_CONTROL") {
                attributeState "open", label:'Open', backgroundColor:"#ffa81e", action: "close", nextState: "closing"
                attributeState "partially open", label:'Partial', backgroundColor:"#d45614", action: "open", nextState: "opening"
                attributeState "closed", label:'Closed', backgroundColor:"#00a0dc", action: "open", nextState: "opening"
                attributeState "opening", label:'Opening', backgroundColor:"#ffa81e", action: "stop", nextState: "partially open"
                attributeState "closing", label:'Closing', backgroundColor:"#00a0dc", action: "stop", nextState: "partially open"
            }
            tileAttribute("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", action:"setLevel", defaultState: true, icon:"st.Home.home9"
            }
        }
        valueTile("power", "device.power", width: 2, height: 2) { state "default", label:'${currentValue} W' }
        valueTile("energy", "device.energy", width: 2, height: 2) { state "default", label:'${currentValue} kWh' }
        standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label:"Refresh", action:"refresh", icon:"st.secondary.refresh-icon"
        }
        standardTile(name: "calibrate", width: 2, height: 2, decoration: "flat") {
            state "default", action:"configure", label:"Calibrate", backgroundColor:"#0000a8"
        }
        standardTile(name: "up", width: 2, height: 2, decoration: "flat") {
            state "default", action:"open", icon:"https://raw.githubusercontent.com/julienbachmann/smartthings/master/fibaro_fgr_222/up.png?v=3"
        }
        standardTile(name: "down", width: 2, height: 2, decoration: "flat") {
            state "default", action:"close", icon:"https://raw.githubusercontent.com/julienbachmann/smartthings/master/fibaro_fgr_222/down.png?v=3"
        }
        standardTile("sync", "device.syncStatus", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", action:"sync" , label:"Sync", backgroundColor:"#00a800"
            state "synced", action:"sync" , label:"Sync", backgroundColor:"#00a800"
            state "syncing" , label:"Syncing", backgroundColor:"#a8a800"
        }

        main(["mainTitle"])

        details([
            "mainTitle",
            "up",
            "power",
            "refresh",
            "down",
            "sync",
            "calibrate"
        ])
    }

    preferences {
        input name: "invert", type: "bool", title: "Invert up/down", description: "Invert up and down actions"
        input name: "openOffset", type: "decimal", title: "Open offset", description: "The percentage from which shutter is displayerd as open"
        input name: "closeOffset", type: "decimal", title: "Close offset", description: "The percentage from which shutter is displayerd as close"
        input name: "offset", type: "decimal", title: "offset", description: "This offset allow to correct the value returned by the device so it match real value"

        section {
            input (type: "paragraph",
            element: "paragraph",
            title: "DEVICE PARAMETERS:",
            description: "Device parameters are used to customise the physical device. " +
            "Refer to the product documentation for a full description of each parameter."
            )

            getFibaroDeviceParameters().findAll( { !it.readonly } ).each {
                // Exclude readonly parameters.

                def lb = (it.description.length() > 0) ? "\n" : ""

                switch(it.type) {
                    case "number":
                        input (
                        name: "configParam${it.id}",
                        title: "#${it.id}: ${it.name}: \n" + it.description + lb +"Default Value: ${it.defaultValue}",
                        type: it.type,
                        range: it.range,
                        required: it.required
                        )
                        break

                    case "enum":
                        input (
                        name: "configParam${it.id}",
                        title: "#${it.id}: ${it.name}: \n ${it.description} \n Default Value: " + it.values.find({ v-> v.value == it.defaultValue }).name,
                        type: "enum",
                        options: it.values.collect({it.name}),
                        required: it.required
                        )
                        break
                }
            }
        }
    }
}

def parse(String description) {
    log.debug("parse ${description}")
    def result = null
    def cmd = zwave.parse(description, [0x20: 1, 0x26: 3, 0x70: 1, 0x32:3])
    if (cmd) {
        result = zwaveEvent(cmd)
        if (result) {
            log.debug("Dispatch events ${result}")
        }
    } else {
        log.debug("Couldn't zwave.parse ${description}")
    }
    return result
}

def correctLevel(value) {
    def result = value
    if (value == "off") {
        result = 0
    }
    if (value == "on" ) {
        result = 100
    }
    result = result - (offset ?: 0)
    if (invert) {
        result = 100 - result
    }
    return result
}

def createWindowShadeEvent(value) {
    def theWindowShade = "partially open"
    if (value >= (openOffset ?: 95)) {
        theWindowShade = "open"
    }
    if (value <= (closeOffset ?: 5)) {
        theWindowShade = "closed"
    }
    return createEvent(name: "windowShade", value: theWindowShade)
}

def createSwitchEvent(value) {
    def switchValue = "on"
    if (value >= (openOffset ?: 95)) {
        switchValue = "on"
    }
    if (value <= (closeOffset ?: 5)) {
        switchValue = "off"
    }
    return createEvent(name: "switch", value: switchValue)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
    logger.debug("basic report ${cmd}")
    def result = []
    if (cmd.value != null) {
        def level = correctLevel(cmd.value)
        result << createEvent(name: "level", value: level, unit: "%")
        result << createWindowShadeEvent(level)
        result << createSwitchEvent(level)
    }
    return result
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
    log.debug("switch multi level report ${cmd.value}")
    def result = []
    if (cmd.value != null) {
        def level = correctLevel(cmd.value)
        result << createEvent(name: "level", value: level, unit: "%")
        result << createWindowShadeEvent(level)
        result << createSwitchEvent(level)
    }
    return result
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    log.debug("other event ${cmd}")
}

def zwaveEvent(physicalgraph.zwave.commands.meterv3.MeterReport cmd) {
    if (cmd.meterType == 1) {
        if (cmd.scale == 0) {
            return createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
        } else if (cmd.scale == 1) {
            return createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kVAh")
        } else if (cmd.scale == 2) {
            return createEvent(name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W")
        } else {
            return createEvent(name: "electric", value: cmd.scaledMeterValue, unit: [
                "pulses",
                "V",
                "A",
                "R/Z",
                ""
            ][cmd.scale - 3])
        }
    }
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
    log.debug("zwaveEvent(): Configuration Report received: ${cmd}")
}

def updated() {
    setSynced()
}

def on() {
    open()
}

def off() {
    close()
}

def stop() {
    def cmds = []
    logger.debug("stop")
    cmds << zwave.switchMultilevelV1.switchMultilevelStopLevelChange().format()
    return delayBetween(cmds, 500)
}

def open() {
    logger.debug("open")
    def currentWindowShade = device.currentValue('windowShade')
    if (currentWindowShade == "opening" || currentWindowShade == "closing") {
        sendEvent(name: "windowShade", value: "partially open")
        sendEvent(name: "switch", value: "on")
        return stop()
    }
    sendEvent(name: "windowShade", value: "opening")
    if (invert) {
        return privateClose()
    }
    else {
        return privateOpen()
    }
}

def close() {
    logger.debug("close")
    def currentWindowShade = device.currentValue('windowShade')
    if (currentWindowShade == "opening" || currentWindowShade == "closing") {
        sendEvent(name: "windowShade", value: "partially open")
        sendEvent(name: "switch", value: "on")
        return stop()
    }
    sendEvent(name: "windowShade", value: "closing")
    if (invert) {
        return privateOpen()
    }
    else {
        return privateClose()
    }
}

def privateOpen() {
    def cmds = []
    cmds << zwave.basicV1.basicSet(value: 0xFF).format()
    log.debug("send CMD: ${cmds}")
    return delayBetween(cmds, 500)
}

def privateClose() {
    def cmds = []
    cmds << zwave.basicV1.basicSet(value: 0).format()
    log.debug("send CMD: ${cmds}")
    return delayBetween(cmds, 500)
}

def presetPosition() {
    setLevel(50)
}

def poll() {
    delayBetween([
        zwave.meterV2.meterGet(scale: 0).format(),
        zwave.meterV2.meterGet(scale: 2).format(),
    ], 1000)
}

def refresh() {
    log.debug("refresh")
    delayBetween([
        zwave.switchBinaryV1.switchBinaryGet().format(),
        zwave.switchMultilevelV3.switchMultilevelGet().format(),
        zwave.meterV2.meterGet(scale: 0).format(),
        zwave.meterV2.meterGet(scale: 2).format(),
    ], 500)
}

def setLevel(level) {
    if (invert) {
        level = 100 - level
    }
    if(level > 99) level = 99
    if (level <= (openOffset ?: 95) && level >= (closeOffset ?: 5)) {
        level = level - (offset ?: 0)
    }

    log.debug("set level ${level}")
    delayBetween([
        zwave.basicV1.basicSet(value: level).format(),
        zwave.switchMultilevelV1.switchMultilevelGet().format()
    ], 10000)
}

def configure() {
    log.debug("configure roller shutter")
    delayBetween([
        zwave.configurationV1.configurationSet(parameterNumber: 29, size: 1, scaledConfigurationValue: 1).format(),
        // start calibration
        zwave.switchMultilevelV1.switchMultilevelGet().format(),
        zwave.meterV2.meterGet(scale: 0).format(),
        zwave.meterV2.meterGet(scale: 2).format(),
    ], 500)
}

def sync() {
    log.debug("sync roller shutter")
    def cmds = []
    sendEvent(name: "syncStatus", value: "syncing", isStateChange: true)
    getFibaroDeviceParameters().findAll( {!it.readonly} ).each {
        def value = null

        if (it.type == "number") {
            value = settings."configParam${it.id}"?.toInteger()
        }

        if (it.type == "enum") {
            value = it.values.find( { v -> v.name == settings."configParam${it.id}" })?.value
        }

        if (value == null) {
            value = it.defaultValue
        }

        cmds << zwave.configurationV1.configurationSet(parameterNumber: it.id, size: it.size, scaledConfigurationValue: value).format()
        cmds << zwave.configurationV1.configurationGet(parameterNumber: it.id).format()

    }
    log.debug("send cmds ${cmds}")
    runIn(0.5 * cmds.size(), setSynced)
    delayBetween(cmds, 500)
}

def setSynced() {
    log.debug("Synced")
    sendEvent(name: "syncStatus", value: "synced", isStateChange: true)
}

private getFibaroDeviceParameters() {
    return [
        [
            id:  3,
            name: "Reports type",
            description:
            '''|Parameters value should be set to 1 if the module operates in Venetian Blind mode.'''.stripMargin(),
            type: "enum",
            values: [
                [name: 'Blind position reports sent to the main controller using Z-Wave Command Class', value: 0],
                [name: 'Blind position reports sent to the main controller using Fibar Command Class', value: 1]
            ],
            defaultValue: 0,
            required: false,
            readonly: false,
            size: 1,
        ],
        [
            id:  10,
            name: "Roller Shutter operating modes",
            description: '',
            type: "enum",
            values: [
                [name: 'Roller Blind Mode, without positioning', value: 0],
                [name: 'Roller Blind Mode, with positioning', value: 1],
                [name: 'Venetian Blind Mode, with positioning', value: 2],
                [name: 'Gate Mode, without positioning', value: 3],
                [name: 'Gate Mode, with positioning', value: 4],
            ],
            defaultValue: 0,
            required: false,
            readonly: false,
            size: 1,
        ],
        [
            id: 12,
            name: "Time of full turn of the slat",
            description:
            '''|In Venetian Blind mode (parameter 10 set to 2) the parameter determines time of full turn of the slats
               |In Gate Mode (parameter 10 set to 3 or 4) the parameter defines the COUNTDOWN time, i.e. the time period after which an open gate starts closing. In any other operating mode the parameter value is irrelevant.
               |Value of 0 means the gate will not close automatically
               |Available settings: 0-65535 (0 - 655,35s)
               |Default setting: 150 (1,5 s)'''.stripMargin(),
            type: "number",
            range: "0..65535",
            defaultValue: 0,
            required: false,
            readonly: false,
            size:2,
        ],
        [
            id: 13,
            name: "Set slats back to previous position",
            description:
            '''|In Venetian Blind Mode (parameter 10 set to 2) the parameter influences slats positioning in various situations. In any other operating mode the parameter value is irrelevant.'''.stripMargin(),
            type: "enum",
            values: [
                [name: 'Slats return to previously set position only in case of the main controller operation', value: 0],
                [name: 'Slats return to previously set position in case of the main controller operation, momentary switch operation, or when the limit switch is reached', value: 1],
                [name: 'Slats return to previously set position in case of the main controller operation, momentary switch operation, when the limit switch is reached or after receiving a “STOP” control frame (Switch Multilevel Stop)', value: 2],
            ],
            defaultValue: 0,
            required: false,
            readonly: false,
            size:1,
        ],
        [
            id: 14,
            name: "Switch type",
            description:
            '''|The parameter settings are relevant for Roller Blind Mode and Venetian Blind Mode (parameter 10 set to 0, 1, 2).'''.stripMargin(),
            type: "enum",
            values: [
                [name: 'Momentary switches', value: 0],
                [name: 'Toggle switches', value: 1],
                [name: 'Single, momentary switch', value: 2],
            ],
            defaultValue: 0,
            required: false,
            readonly: false,
            size:1,
        ],
        [
            id: 17,
            name: "Turning off relays after reaching a limit switch",
            description:
            '''|In Roller Blind Mode and Venetian Blind Mode - turning off relays after reaching a limit switch
               |In Gate Mode (parameter 10 set to 3 or 4) the parameter determines a time period after which a gate will start closing after a S2 contact has been disconnected. In this mode, time to turn off the Roller Shutter relays after reaching a limit switch is set to 3 seconds and cannot be modified.
               |Available settings:
               |0 – gate will not close automatically
               |1-255 (0.1-25.5s, 0.1 step)'''.stripMargin(),
            type: "number",
            range: "0..255",
            defaultValue: 0,
            required: false,
            readonly: false,
            size:1,
        ],
        [
            id: 18,
            name: "Motor operation detection",
            description:
            '''|Power threshold to be interpreted as reaching a limit switch.
               |Available settings: 0 - 255 (1-255 W)
               |The value of 0 means reaching a limit switch will not be detected
               |Default setting: 10 (10W).'''.stripMargin(),
            type: "number",
            range: "0..255",
            defaultValue: 0,
            required: false,
            readonly: false,
            size:1,
        ],
        [
            id: 22,
            name: "Motor operation time.",
            description:
            '''|Time period for the motor to continue operation.
               |Available settings: 0 – 65535 (0 – 65535s)
               |The value of 0 means the function is disabled.
               |Default setting: 240 (240s. – 4 minutes)'''.stripMargin(),
            type: "number",
            range: "0..65535",
            defaultValue: 0,
            required: false,
            readonly: false,
            size:2,
        ],
        [
            id: 30,
            name: "Response to general alarm",
            description: '',
            type: "enum",
            values: [
                [name: 'No reaction', value: 0],
                [name: 'Open blind', value: 1],
                [name: 'Close blind', value: 2],
            ],
            defaultValue: 2,
            required: false,
            readonly: false,
            size:1,
        ],
        [
            id: 31,
            name: "Response to flooding alarm",
            description: '',
            type: "enum",
            values: [
                [name: 'No reaction', value: 0],
                [name: 'Open blind', value: 1],
                [name: 'Close blind', value: 2],
            ],
            defaultValue: 0,
            required: false,
            readonly: false,
            size:1,
        ],
        [
            id: 32,
            name: "Response to smoke, CO or CO2 alarm",
            description: '',
            type: "enum",
            values: [
                [name: 'No reaction', value: 0],
                [name: 'Open blind', value: 1],
                [name: 'Close blind', value: 2],
            ],
            defaultValue: 1,
            required: false,
            readonly: false,
            size:1,
        ],
        [
            id: 33,
            name: "Response to temperature alarm",
            description: '',
            type: "enum",
            values: [
                [name: 'No reaction', value: 0],
                [name: 'Open blind', value: 1],
                [name: 'Close blind', value: 2],
            ],
            defaultValue: 1,
            required: false,
            readonly: false,
            size:1,
        ],
        [
            id: 35,
            name: "Managing slats in response to alarm.",
            description: '',
            type: "enum",
            values: [
                [name: 'Do not change slats position - slats return to the last set position', value: 0],
                [name: 'Set slats to their extreme position', value: 1],
            ],
            defaultValue: 1,
            required: false,
            readonly: false,
            size:1,
        ],
        [
            id: 40,
            name: "Power reports",
            description:
            '''|Power level change that will result in new power value report being sent.\
               |The parameter defines a change that needs to occur in order to trigger the report. The value is a percentage of the previous report.
               |Power report threshold available settings: 1-100 (1-100%).
               |Value of 0 means the reports are turned off.'''.stripMargin(),
            type: "number",
            range: "0..100",
            defaultValue: 10,
            required: false,
            readonly: false,
            size:1,
        ],
        [
            id: 42,
            name: "Periodic power or energy reports",
            description:
            '''|This parameter determines a time period between consecutive reports.
               |Available settings:
               |0 – periodic reports are disabled
               |1-65534 (1-65534s)'''.stripMargin(),
            type: "number",
            range: "0..65534",
            defaultValue: 3600,
            required: false,
            readonly: false,
            size:2,
        ],
        [
            id: 42,
            name: "Energy reports",
            description:
            '''|Energy level change which will result in new energy value report being sent. The parameter defines a change that needs to occur in order to trigger the report.
               |Available settings:
               |0 – reports are disabled
               |1-254 (0.01-2.54kWh)'''.stripMargin(),
            type: "number",
            range: "0..254",
            defaultValue: 10,
            required: false,
            readonly: false,
            size:1,
        ],
        [
            id: 44,
            name: "Self-measurement",
            description: 'The device may include power and energy used by itself in reports sent to the main controller',
            type: "enum",
            values: [
                [name: 'self-measurement inactive', value: 0],
                [name: 'self-measurement active', value: 1],
            ],
            defaultValue: 0,
            required: false,
            readonly: false,
            size:1,
        ],
    ]
}
