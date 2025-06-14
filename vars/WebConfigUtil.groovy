package com.util

import groovy.xml.XmlNodePrinter
import groovy.xml.XmlParser

def call(String pathOrContent, String operation, String nodeName, String newValue = null) {
    def result = [success: false, message: "", modifiedContent: null]

    def supportedOps = ["add", "delete", "update", "isexist"]
    if (!supportedOps.contains(operation.toLowerCase())) {
        result.message = "Unsupported operation '${operation}'. Supported operations: ${supportedOps.join(', ')}"
        return result
    }

    def isFile = false
    def xmlContent = null
    def filePath = pathOrContent

    // Check if pathOrContent is a path to an existing file
    def file = new File(pathOrContent)
    if (file.exists() && file.isFile()) {
        isFile = true
        try {
            xmlContent = file.text
        } catch (Exception e) {
            result.message = "Failed to read file '${filePath}': ${e.message}"
            return result
        }
    } else {
        xmlContent = pathOrContent
    }

    if (!xmlContent?.trim()) {
        result.message = "Error: web.config content is empty or null."
        return result
    }

    try {
        def xml = new XmlParser().parseText(xmlContent)
        def targetNode = xml.'**'.find { it.name().toString() == nodeName }

        switch (operation.toLowerCase()) {
            case "isexist":
                result.success = (targetNode != null)
                result.message = result.success ? "Node '${nodeName}' exists." : "Node '${nodeName}' does not exist."
                break

            case "add":
                if (targetNode) {
                    result.message = "Node '${nodeName}' already exists."
                } else {
                    xml.appendNode(nodeName, newValue ?: "")
                    result.success = true
                    result.message = "Node '${nodeName}' added successfully."
                }
                break

            case "update":
                if (targetNode) {
                    // Update node text value
                    targetNode.value = newValue
                    result.success = true
                    result.message = "Node '${nodeName}' updated successfully."
                } else {
                    result.message = "Node '${nodeName}' not found for update."
                }
                break

            case "delete":
                if (targetNode) {
                    targetNode.parent().remove(targetNode)
                    result.success = true
                    result.message = "Node '${nodeName}' deleted successfully."
                } else {
                    result.message = "Node '${nodeName}' not found to delete."
                }
                break
        }

        // If modified, serialize XML back to string
        if (result.success && operation.toLowerCase() != 'isexist') {
            def sw = new StringWriter()
            new XmlNodePrinter(new PrintWriter(sw)).print(xml)
            result.modifiedContent = sw.toString()

            // If file, write back to disk automatically
            if (isFile) {
                try {
                    file.write(result.modifiedContent)
                } catch (Exception e) {
                    result.success = false
                    result.message = "Failed to write updated content to file '${filePath}': ${e.message}"
                }
            }
        }
    } catch (Exception e) {
        result.message = "Exception occurred during XML parsing or processing: ${e.message}"
    }

    return result
}
