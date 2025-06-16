package com.suangfu

class FileCompareUtil {

    /**
     * Recursively list all filenames (lowercased) in a directory.
     * @param dirPath directory path
     * @return set of filenames (lowercase)
     */
    static Set<String> listFilenamesRecursively(String dirPath) {
        def command = "dir /s /b \"${dirPath}\""
        def output = bat(script: command, returnStdout: true).trim()
        def lines = output.readLines()
        return lines.collect { it.tokenize('\\')[-1].toLowerCase() }.toSet()
    }

    /**
     * Map filename (lowercased) to full path
     * @param dirPath directory path
     * @return map of filename -> full path
     */
    static Map<String, String> mapFilenamesToPaths(String dirPath) {
        def command = "dir /s /b \"${dirPath}\""
        def output = bat(script: command, returnStdout: true).trim()
        def lines = output.readLines()
        def map = [:]
        lines.each { path ->
            def name = path.tokenize('\\')[-1].toLowerCase()
            map[name] = path
        }
        return map
    }

    /**
     * Run PowerShell script to compare two files line-by-line and report differences.
     * Returns null if files cannot be read or are identical, otherwise returns diff string.
     */
static String filesAreDifferentWithLineNumbers(String file1, String file2) {
    def script = '''
function SafeReadLines {
    param ([string] $filePath)
    try {
        $content = Get-Content -Raw -LiteralPath $filePath -Encoding UTF8 -ErrorAction Stop | Out-String -Stream
        $lines = $content -replace "`r`n", "`n" -split "`n"
        return $lines
    } catch {
        Write-Output "CANNOT_READ_FILE: $filePath"
        return $null
    }
}

$file1Lines = SafeReadLines -filePath '<<FILE1>>'
$file2Lines = SafeReadLines -filePath '<<FILE2>>'

if (-not $file1Lines -or -not $file2Lines) {
    Write-Output "CANNOT_READ_FILE"
    exit 1
}

$maxLines = [Math]::Max($file1Lines.Count, $file2Lines.Count)
$diffs = @()

for ($i = 0; $i -lt $maxLines; $i++) {
    $lineNum = $i + 1
    $line1 = if ($i -lt $file1Lines.Count -and $file1Lines[$i]) { $file1Lines[$i].TrimEnd() } else { '<no line>' }
    $line2 = if ($i -lt $file2Lines.Count -and $file2Lines[$i]) { $file2Lines[$i].TrimEnd() } else { '<no line>' }

    if ($line1 -ne $line2) {
        $diffs += "Difference at line ${lineNum}:"
        $diffs += "  <<FILE1>>: ${line1}"
        $diffs += "  <<FILE2>>: ${line2}"
    }
}

if ($diffs.Count -gt 0) {
    $diffs -join "`n"
} else {
    ""
}
'''
    // Inject actual file paths safely
    script = script.replace('<<FILE1>>', file1.replace("\\", "\\\\"))
                   .replace('<<FILE2>>', file2.replace("\\", "\\\\"))

    def output = powershell(returnStdout: true, script: script).trim()
    return (!output || output.contains("CANNOT_READ_FILE")) ? null : output
}


    /**
     * Compare filenames and contents of two directories.
     * Throws exception on any difference, otherwise returns success message.
     * @param dir1
     * @param dir2
     * @return summary message
     */
    static String compareFilenames(String dir1, String dir2) {
        def filenames1 = listFilenamesRecursively(dir1)
        def filenames2 = listFilenamesRecursively(dir2)

        def fileMap1 = mapFilenamesToPaths(dir1)
        def fileMap2 = mapFilenamesToPaths(dir2)

        def commonFilenames = filenames1.intersect(filenames2)
        def onlyInDir1 = filenames1 - filenames2
        def onlyInDir2 = filenames2 - filenames1

        StringBuilder report = new StringBuilder()
        report.append("📁 Total files in ${dir1}: ${filenames1.size()}\n")
        report.append("📁 Total files in ${dir2}: ${filenames2.size()}\n")
        report.append("✅ Common filenames: ${commonFilenames.size()}\n")

        def diffFiles = []

        commonFilenames.each { filename ->
            def file1 = fileMap1[filename]
            def file2 = fileMap2[filename]

            try {
                def diffOutput = filesAreDifferentWithLineNumbers(file1, file2)
                if (diffOutput) {
                    report.append("❌ Differences in ${filename}:\n${diffOutput}\n")
                    diffFiles << filename
                }
            } catch (Exception e) {
                report.append("⚠️ Skipping unreadable file: ${filename} (${e.message})\n")
            }
        }

        if (!onlyInDir1.isEmpty()) {
            report.append("⚠️ Files only in ${dir1}:\n")
            onlyInDir1.each { report.append("  ${it}\n") }
        }
        if (!onlyInDir2.isEmpty()) {
            report.append("⚠️ Files only in ${dir2}:\n")
            onlyInDir2.each { report.append("  ${it}\n") }
        }
        if (!onlyInDir1.isEmpty() || !onlyInDir2.isEmpty() || !diffFiles.isEmpty()) {
            throw new Exception("❗ Differences found: filenames and/or content do not match.\n" + report.toString())
        }

        report.append("🎉 All filenames and content match between directories.\n")
        return report.toString()
    }
}
