// vars/manageWebConfig.groovy

def call(String configContent, String operation, String nodeName, String newValue = null) {
    def result = [success: false, message: ""]

    def supportedOps = ["add", "delete", "update", "isexist"]
    if (!supportedOps.contains(operation.toLowerCase())) {
        result.message = "Unsupported operation '${operation}'. Supported operations: ${supportedOps.join(', ')}"
        return result
    }

    if (!configContent?.trim()) {
        result.message = "Error: web.config content is empty or null."
        return result
    }

    try {
        def xml = new XmlParser().parseText(configContent)
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
                    def parentNode = xml
                    parentNode.appendNode(nodeName, newValue ?: "")
                    def sw = new StringWriter()
                    new XmlNodePrinter(new PrintWriter(sw)).print(xml)
                    result.success = true
                    result.message = "Node '${nodeName}' added successfully."
                    result.modifiedContent = sw.toString()
                }
                break

            case "update":
                if (targetNode) {
                    targetNode.value = newValue
                    def sw = new StringWriter()
                    new XmlNodePrinter(new PrintWriter(sw)).print(xml)
                    result.success = true
                    result.message = "Node '${nodeName}' updated successfully."
                    result.modifiedContent = sw.toString()
                } else {
                    result.message = "Node '${nodeName}' not found for update."
                }
                break

            case "delete":
                if (targetNode) {
                    def parent = targetNode.parent()
                    parent.remove(targetNode)
                    def sw = new StringWriter()
                    new XmlNodePrinter(new PrintWriter(sw)).print(xml)
                    result.success = true
                    result.message = "Node '${nodeName}' deleted successfully."
                    result.modifiedContent = sw.toString()
                } else {
                    result.message = "Node '${nodeName}' not found to delete."
                }
                break
        }
    } catch (Exception e) {
        result.message = "Exception occurred during XML parsing or processing: ${e.message}"
    }

    return result
}
