package com.dexter.cast

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

/**
 * DLNA (UPnP) casting, for smart TVs that are not Chromecasts. A TV announces itself over SSDP, its
 * description file names an AVTransport control address, and two SOAP calls show a picture:
 * SetAVTransportURI, then Play.
 */
const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"

/** The SSDP search that asks every media renderer on the network to answer. */
val SSDP_SEARCH: String = listOf(
    "M-SEARCH * HTTP/1.1",
    "HOST: 239.255.255.250:1900",
    "MAN: \"ssdp:discover\"",
    "MX: 2",
    "ST: $AV_TRANSPORT",
    "",
    "",
).joinToString("\r\n")

/** The LOCATION header of an SSDP answer: where the device's description file is. */
fun ssdpLocation(response: String): String? = response.lineSequence()
    .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
    ?.substringAfter(':')?.trim()?.takeIf { it.startsWith("http") }

/** A renderer's name and the absolute address its AVTransport SOAP calls go to. */
data class DlnaRenderer(val name: String, val controlUrl: String)

/** Reads a UPnP device description fetched from [location]. Null when it has no AVTransport service. */
fun parseDlnaDescription(xml: String, location: String): DlnaRenderer? {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        // A description file never needs outside entities, and refusing them closes the XXE hole.
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }
    val doc = runCatching { factory.newDocumentBuilder().parse(InputSource(StringReader(xml))) }.getOrNull() ?: return null
    fun Element.child(name: String): String? = getElementsByTagName(name).item(0)?.textContent?.trim()
    val root = doc.documentElement
    val name = root.child("friendlyName") ?: "TV"
    val services = root.getElementsByTagName("service")
    for (i in 0 until services.length) {
        val service = services.item(i) as Element
        if (service.child("serviceType")?.startsWith("urn:schemas-upnp-org:service:AVTransport:") == true) {
            val control = service.child("controlURL") ?: return null
            val base = root.child("URLBase")?.takeIf { it.isNotBlank() } ?: location
            return DlnaRenderer(name, URI(base).resolve(control).toString())
        }
    }
    return null
}

private fun xmlEscape(text: String) =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

/** The SOAP body for an AVTransport [action] with [arguments], in order. */
fun dlnaSoapBody(action: String, arguments: List<Pair<String, String>>): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
    append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
    append("<s:Body><u:$action xmlns:u=\"$AV_TRANSPORT\">")
    arguments.forEach { (name, value) -> append("<$name>${xmlEscape(value)}</$name>") }
    append("</u:$action></s:Body></s:Envelope>")
}

/** DIDL-Lite metadata describing one photo, which many TVs need before they show it. */
fun dlnaPhotoMetadata(url: String, title: String): String =
    "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
        "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
        "<dc:title>${xmlEscape(title)}</dc:title><upnp:class>object.item.imageItem.photo</upnp:class>" +
        "<res protocolInfo=\"http-get:*:${imageTypeFor(url)}:*\">${xmlEscape(url)}</res></item></DIDL-Lite>"
