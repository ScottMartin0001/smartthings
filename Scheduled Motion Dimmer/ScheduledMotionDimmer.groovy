/**
 *  Scheduled Motion Dimmer
 *
 *  Copyright 2015 Brian Keifer
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Scheduled Motion Dimmer",
    namespace: "bkeifer",
    author: "Brian Keifer",
    description: "Nobody likes being blinded by the bathroom lights when they get up in the middle of the night.  This app uses motion to trigger lights to come on at specific levels during various times during the day.",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Lights:") {
    	input "lights", "capability.switchLevel", multiple: true, required: true
	}
	section("Motion Sensor:") {
    	input "motionSensor", "capability.motionSensor", multiple: true, required: true
	}

    for (int i = 1; i <= 4; i++) {
        section ("Time Interval #${i}") {
            input "startTime_${i}", "time", title: "Starting at:", required: false
            input "endTime_${i}", "time", title: "Ending at:", required: false
            input "level_${i}", "number", title: "Level in percent (1-100)", required: false
            input "duration_${i}", "number", title: "Minutes to keep the light on after motion stops:", defaultValue:5, required: false
        }
    }
}


def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}


def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}


def initialize() {
	subscribe(motionSensor, "motion", motionHandler)
}


def motionHandler(evt) {
    if (evt.value == "active") {
        checkIntervals()
    }
}


private checkIntervals() {

    // def rightNow = now()
    // log.debug "time: ${startTime_1}"
    // log.debug "timeToday(now): " + timeToday(startTime_1)
    // log.debug "timeToday(now, location.timeZone): " + timeToday(startTime_1, location.timeZone)
    // log.debug "The time zone for this location is: ${location.timeZone}"
    // log.debug "The zip code for this location: ${location.zipCode}"
    // log.debug ""
    for (int i = 4; i > 0; i--) {
        if (settings."startTime_${i}" && settings."endTime_${i}" && settings."duration_${i}") {

            log.debug("startTime_${i}: " + timeToday(settings."startTime_${i}", location.timeZone))
            log.debug("endTime_${i}: " + timeToday(settings."endTime_${i}", location.timeZone))

            if (timeInRange(i)) {
                def offDelay = settings."duration_${i}" * 60
                def level = settings."level_${i}"
                log.debug("Interval ${i} matches.  Turning lights on to {$level}!")
                stash("Turning ${lights} on to ${level}! (Interval ${i})")
                lights?.setLevel(level)
                runIn(offDelay, lightsOff)
                break

            }
        }
    }
}


private timeInRange(int i) {
    def rightNow  = now()
    def startTime = timeToday(settings."startTime_${i}").time
    def endTime   = timeToday(settings."endTime_${i}").time

    return (startTime > endTime ?
                    ( rightNow >= startTime || rightNow <= endTime) :
                    ( rightNow >= startTime && rightNow <= endTime))
}


def lightsOff() {
    stash("Turning ${lights} off!")
    lights?.off()
}


def stash(msg) {
	log.debug(msg)
	TimeZone.setDefault(TimeZone.getTimeZone('UTC'))
	def dateNow = new Date()
    def isoDateNow = dateNow.format("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    def json = "{"
    json += "\"date\":\"${dateNow}\","
    json += "\"isoDate\":\"${isoDateNow}\","
    json += "\"name\":\"log\","
    json += "\"message\":\"${msg}\","
    json += "\"smartapp\":\"${app.name}\""
    json += "}"
    def params = [
    	uri: "http://graphite.valinor.net:5279",
        body: json
    ]
    try {
        httpPostJson(params)
    } catch ( groovyx.net.http.HttpResponseException ex ) {
       	log.debug "Unexpected response error: ${ex.statusCode}"
    }
}