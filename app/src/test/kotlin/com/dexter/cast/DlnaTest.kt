package com.dexter.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaTest {
    @Test
    fun locationComesFromTheSsdpAnswer() {
        val answer = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLocation: http://192.168.1.20:8080/desc.xml\r\nST: $AV_TRANSPORT\r\n\r\n"
        assertEquals("http://192.168.1.20:8080/desc.xml", ssdpLocation(answer))
    }

    @Test
    fun descriptionGivesNameAndAbsoluteControlUrl() {
        val xml = """<?xml version="1.0"?><root xmlns="urn:schemas-upnp-org:device-1-0"><device>
            <friendlyName>Living Room TV</friendlyName><serviceList>
            <service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType><controlURL>/rc</controlURL></service>
            <service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>/upnp/control/AVTransport1</controlURL></service>
            </serviceList></device></root>"""
        val renderer = parseDlnaDescription(xml, "http://192.168.1.20:8080/desc.xml")!!
        assertEquals("Living Room TV", renderer.name)
        assertEquals("http://192.168.1.20:8080/upnp/control/AVTransport1", renderer.controlUrl)
    }

    @Test
    fun deviceWithoutAvTransportIsSkipped() {
        assertNull(parseDlnaDescription("<root><device><friendlyName>Speaker</friendlyName></device></root>", "http://h/"))
    }

    @Test
    fun soapEscapesItsArguments() {
        val body = dlnaSoapBody("SetAVTransportURI", listOf("InstanceID" to "0", "CurrentURI" to "http://x/a?b=1&c=2"))
        assertTrue(body.contains("<CurrentURI>http://x/a?b=1&amp;c=2</CurrentURI>"))
        assertTrue(body.contains("<u:SetAVTransportURI xmlns:u=\"$AV_TRANSPORT\">"))
    }
}
