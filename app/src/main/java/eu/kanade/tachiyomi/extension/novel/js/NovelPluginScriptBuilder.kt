package eu.kanade.tachiyomi.extension.novel.js

/**
 * Wraps a LNReader-compatible plugin script for execution in the JS runtime.
 *
 * LNReader plugins use CommonJS-style module exports. This wrapper:
 * 1. Creates a `module`/`exports` context.
 * 2. Evaluates the plugin script.
 * 3. Extracts the default export (either a class or an object).
 * 4. Instantiates the class if needed.
 * 5. Assigns the result to `__plugin` for the runtime to use.
 */
class NovelPluginScriptBuilder {

    fun wrap(script: String, moduleName: String): String {
        return buildString {
            appendLine("var module = { exports: {} };")
            appendLine("var exports = module.exports;")
            appendLine("(function() {")
            appendLine(script)
            appendLine("})();")
            appendLine("var __pluginExport = exports.default || module.exports.default || module.exports;")
            appendLine("if (!__pluginExport) throw new Error(\"Plugin $moduleName: no export found\");")
            appendLine("if (typeof __pluginExport === \"function\") {")
            appendLine("    __plugin = new __pluginExport();")
            appendLine("} else {")
            appendLine("    __plugin = __pluginExport;")
            appendLine("}")
        }
    }

    /**
     * Build the call to a plugin method with JSON arguments.
     * The runtime evaluates this to invoke a method on the plugin instance.
     */
    fun callMethod(methodName: String, vararg args: String): String {
        val argList = args.joinToString(", ") { it }
        return "JSON.stringify(__plugin.$methodName($argList))"
    }

    /**
     * Build the call to a plugin method that returns a JSON string.
     */
    fun callMethodRaw(methodName: String, vararg args: String): String {
        val argList = args.joinToString(", ") { it }
        return "__plugin.$methodName($argList)"
    }
}
