package spam.blocker.util

import org.w3c.dom.Document
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory


object Xml {
    fun parse(
        bytes: ByteArray,
        expression: String,
    ): List<Map<String, String>> {
        val document = parseXml(bytes)
        val xPath = XPathFactory.newInstance().newXPath()

        val nodes =
            xPath.evaluate(expression, document, XPathConstants.NODESET) as NodeList

        val list = mutableListOf<Map<String, String>>()

        for (i in 0 until nodes.length) {
            list.add(
                mapOf(
                    "pattern" to nodes.item(i).textContent
                )
            )
        }
        return list
    }

    private fun parseXml(bytes: ByteArray): Document {
        val documentBuilderFactory = DocumentBuilderFactory.newInstance()
        val documentBuilder = documentBuilderFactory.newDocumentBuilder()
        val inputStream = ByteArrayInputStream(bytes)
        return documentBuilder.parse(inputStream)
    }
}