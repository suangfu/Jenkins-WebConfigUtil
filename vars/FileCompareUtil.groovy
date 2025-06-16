def listFilenamesRecursively(String dirPath) {
    def command = "dir /s /b \"${dirPath}\""
    def output = bat(script: command, returnStdout: true).trim()
    def lines = output.readLines()
    return lines.collect { it.tokenize('\\')[-1].toLowerCase() }.toSet()  // case-insensitive filenames
}

def mapFilenamesToPaths(String dirPath) {
    def command = "dir /s /b \"${dirPath}\""
    def output = bat(script: command, returnStdout: true).trim()
    def lines = output.readLines()
    def map = [:]
    lines.each { path ->
        def name = path.tokenize('\\')[-1].toLowerCase()  // lower case key for matching
        map[name] = path
    }
    return map
}

def filesAreDifferentWithLineNumbers(String file1, String file2) {
    def script = """
    function SafeReadLines {
        param (
            [string] \$filePath
        )
        try {
            \$content = Get-Content -Raw -LiteralPath \$filePath -Encoding UTF8 -ErrorAction Stop | Out-String -Stream
            \$lines = \$content -replace "`r`n", "`n" -split "`n"
            return \$lines
        } catch {
            Write-Output "CANNOT_READ_FILE: \$filePath"
            return \$null
        }
    }

    \$file1Lines = SafeReadLines -filePath '${file1}'
    \$file2Lines = SafeReadLines -filePath '${file2}'

    if (-not \$file1Lines -or -not \$file2Lines) {
        Write-Output "CANNOT_READ_FILE"
        exit 1
    }

    \$maxLines = [Math]::Max(\$file1Lines.Count, \$file2Lines.Count)
    \$diffs = @()

    for (\$i = 0; \$i -lt \$maxLines; \$i++) {
        \$lineNum = \$i + 1
        \$line1 = if (\$i -lt \$file1Lines.Count -and \$file1Lines[\$i]) { \$file1Lines[\$i].TrimEnd() } else { '<no line>' }
        \$line2 = if (\$i -lt \$file2Lines.Count -and \$file2Lines[\$i]) { \$file2Lines[\$i].TrimEnd() } else { '<no line>' }

        if (\$line1 -ne \$line2) {
            \$diffs += "Difference at line \${lineNum}:"
            \$diffs += "  ${file1}: \${line1}"
            \$diffs += "  ${file2}: \${line2}"
        }
    }

    if (\$diffs.Count -gt 0) {
        \$diffs -join "`n"
    } else {
        ""
    }
    """
    def output = powershell(returnStdout: true, script: script).trim()
    return (!output || output.contains("CANNOT_READ_FILE")) ? null : output
}


def compareFilenames(String dir1, String dir2) {
    def filenames1 = listFilenamesRecursively(dir1)
    def filenames2 = listFilenamesRecursively(dir2)

    def fileMap1 = mapFilenamesToPaths(dir1)
    def fileMap2 = mapFilenamesToPaths(dir2)

    def commonFilenames = filenames1.intersect(filenames2)
    def onlyInDir1 = filenames1 - filenames2
    def onlyInDir2 = filenames2 - filenames1

    echo "📁 Total files in ${dir1}: ${filenames1.size()}"
    echo "📁 Total files in ${dir2}: ${filenames2.size()}"
    echo "✅ Common filenames: ${commonFilenames.size()}"

    // Uncomment if you want to see files only in one dir
    // if (!onlyInDir1.isEmpty()) {
    //     echo "⚠️ Files only in ${dir1}:"
    //     onlyInDir1.each { echo "  ${it}" }
    // }
    // if (!onlyInDir2.isEmpty()) {
    //     echo "⚠️ Files only in ${dir2}:"
    //     onlyInDir2.each { echo "  ${it}" }
    // }

    def diffFiles = []

    commonFilenames.each { filename ->
        def file1 = fileMap1[filename]
        def file2 = fileMap2[filename]

 def diffOutput
    try {
        diffOutput = filesAreDifferentWithLineNumbers(file1, file2)
    } catch (Exception e) {
        echo "⚠️ Skipping unreadable file: ${filename} (${e.message})"
        return
    }

    if (diffOutput) {
        echo "❌ Differences in ${filename}:"
        echo diffOutput
        diffFiles << filename
    }

        // def diffOutput = filesAreDifferentWithLineNumbers(file1, file2)
        // if (diffOutput) {
        //     echo "❌ Differences in ${filename}:"
        //     echo diffOutput
        //     diffFiles << filename
        // }
    }

    if (!onlyInDir1.isEmpty() || !onlyInDir2.isEmpty() || !diffFiles.isEmpty()) {
        error("❗ Differences found: filenames and/or content do not match.")
    } else {
        echo "🎉 All filenames and content match between directories."
    }
}