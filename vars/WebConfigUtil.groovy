def call(String configContent, String operation, String nodeName, String keyName = null, String newValue = null) {
    def result = [success: false, message: ""]
    def supportedOps = ["add", "delete", "update", "isexist"]

    if (!supportedOps.contains(operation.toLowerCase())) {
        result.message = "Unsupported operation '${operation}'."
        return result
    }

    if (!configContent?.trim()) {
        result.message = "web.config content is empty or null."
        return result
    }

    if (!keyName?.trim()) {
        result.message = "Key name is required for this operation."
        return result
    }

    try {
        def xml = new XmlParser().parseText(configContent)
        def parentNode = xml.'**'.find { it.name().toString() == nodeName }

        if (!parentNode) {
            result.message = "Node '${nodeName}' not found."
            return result
        }

        def targetNode = parentNode.'add'.find { it.@key == keyName }

        switch (operation.toLowerCase()) {
            case "isexist":
                result.success = (targetNode != null)
                result.message = result.success ? "Key '${keyName}' exists." : "Key '${keyName}' does not exist."
                break

            case "add":
                if (targetNode) {
                    result.message = "Key '${keyName}' already exists."
                } else {
                    parentNode.appendNode('add', [key: keyName, value: newValue ?: ""])
                    def sw = new StringWriter()
                    new XmlNodePrinter(new PrintWriter(sw)).print(xml)
                    result.success = true
                    result.message = "Key '${keyName}' added."
                    result.modifiedContent = sw.toString()
                }
                break

            case "update":
                if (targetNode) {
                    targetNode.@value = newValue
                    def sw = new StringWriter()
                    new XmlNodePrinter(new PrintWriter(sw)).print(xml)
                    result.success = true
                    result.message = "Key '${keyName}' updated."
                    result.modifiedContent = sw.toString()
                } else {
                    result.message = "Key '${keyName}' not found to update."
                }
                break

            case "delete":
                if (targetNode) {
                    parentNode.remove(targetNode)
                    def sw = new StringWriter()
                    new XmlNodePrinter(new PrintWriter(sw)).print(xml)
                    result.success = true
                    result.message = "Key '${keyName}' deleted."
                    result.modifiedContent = sw.toString()
                } else {
                    result.message = "Key '${keyName}' not found to delete."
                }
                break
        }

    } catch (e) {
        result.message = "Exception: ${e.message}"
    }

    return result
}
