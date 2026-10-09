package com.example.terminalmodule.internal

import java.lang.reflect.Modifier

/** Diagnostic par réflexion : aucune dépendance SDK à la compilation, aucune donnée sensible. */
internal object SdkInspector {

    fun dump(className: String, constantPrefix: String? = null) {
        val cls = try {
            Class.forName(className)
        } catch (e: Throwable) {
            TerminalLog.w("[inspect] classe introuvable : $className")
            return
        }
        TerminalLog.i("[inspect] ${cls.name}")

        if (cls.isEnum) {
            TerminalLog.i("[inspect]   valeurs : " + cls.enumConstants.joinToString(", "))
            return
        }

        cls.fields
            .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
            .filter { constantPrefix == null || it.name.startsWith(constantPrefix) }
            .forEach { TerminalLog.i("[inspect]   const ${it.name} = ${it.getInt(null)}") }

        if (constantPrefix == null) {
            cls.methods
                .filter { it.declaringClass != Any::class.java }
                .sortedBy { it.name }
                .forEach { m ->
                    val params = m.parameterTypes.joinToString { it.simpleName }
                    TerminalLog.i("[inspect]   ${m.returnType.simpleName} ${m.name}($params)")
                }
        }
    }
}