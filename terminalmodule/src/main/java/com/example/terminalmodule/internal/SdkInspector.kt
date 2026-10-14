package com.example.terminalmodule.internal

import java.lang.reflect.Modifier

/** Diagnostic par réflexion : aucune dépendance SDK à la compilation, aucune donnée sensible. */
 object SdkInspector {

    /** Liste les constantes (filtrables par préfixe) ou les méthodes d'une classe. */
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

    /** Valeurs de l'énumération passée en paramètre d'une méthode (ex. setAlgorithmMode). */
    fun enumValuesOf(className: String, methodName: String): List<String> = runCatching {
        Class.forName(className).methods
            .filter { it.name == methodName && it.parameterTypes.size == 1 && it.parameterTypes[0].isEnum }
            .flatMap { method -> method.parameterTypes[0].enumConstants.map { (it as Enum<*>).name } }
            .distinct()
    }.getOrDefault(emptyList())

    /** Affiche, pour chaque réglage, les valeurs possibles de son énumération. */
    fun dumpSetterEnums(className: String, methodNames: List<String>) {
        methodNames.forEach { name ->
            val values = enumValuesOf(className, name)
            TerminalLog.i(
                "[inspect] $name : " +
                        if (values.isEmpty()) "introuvable ou pas une énumération" else values.joinToString(", ")
            )
        }
    }
}