package calculator

import java.math.BigInteger
import java.util.LinkedList

enum class InputType(regexString: String) {
    HELP("/help"),
    EXIT("/exit"),
    ASSIGNMENT("\\w+\\s*=.*"),
    SINGLE_NUMBER("^(?:[\\+-]?\\d+)$"),
    EMPTY_LINE("^\\s*$"),
    LINE("[^\\/].*"),
    ;
    val regex = regexString.toRegex()
    companion object {
        fun valueOfOrNull(string: String): InputType? {
            values().forEach {
                if (string.matches(it.regex)) {
                    return it
                }
            }
            return null
        }
    }
}

const val DELIMITER = "\\s*(?=\\b|\\(|\\)|$)"

interface OperandOrOperator

data class Operand(val name: String? = null, val value: BigInteger? = null): OperandOrOperator {
    fun getCurrentValue(): BigInteger? = if (name != null) getValue(name) else value
    override fun toString() = name ?: value!!.toString()
}

enum class OperandType(regexString: String, val toOperand: (String) -> Operand) {
    SINGLE_NUMBER("^(?:[\\+-]?\\d+)$DELIMITER", { string: String -> Operand(value = string.trim().toBigInteger())}),
    IDENTIFIER("^[a-zA-Z]+$DELIMITER", {string: String -> Operand(name = string.trim())}),;
    val regex = regexString.toRegex()
}

enum class Operator(val char: Char, regexString: String, val operation: (BigInteger, BigInteger) -> BigInteger): OperandOrOperator {
    LEFT_PARENTHESIS('(', "\\(", { _: java.math.BigInteger, _: java.math.BigInteger -> throw UnsupportedOperationException()}),
    ADD('+', "^(?:\\++)|^(?:(?:--)+)$DELIMITER", BigInteger::plus),
    SUBTRACT('-', "^-(?:--)*$DELIMITER", BigInteger::minus),
    MULTIPLY('*', "^\\*$DELIMITER", BigInteger::times),
    DIVIDE('/', "^/$DELIMITER", BigInteger::div),
    POWER('^', "^\\^$DELIMITER", { a: java.math.BigInteger, b: java.math.BigInteger -> a.pow(b.toInt())}),
    RIGHT_PARENTHESIS(')', "\\)", { _: java.math.BigInteger, _: java.math.BigInteger -> throw UnsupportedOperationException()}),
    ;
    val regex = regexString.toRegex()
    companion object {
        fun valueOfOrNull(string: String): Operator? {
            values().forEach {
                val matchResult = it.regex.find(string)
                if (matchResult != null) {
                    return it
                }
                if (string.matches(it.regex)) {
                    return it
                }
            }
            return null
        }
    }
    override fun toString() = "$char"
}

val variables = mutableMapOf<String, BigInteger>()

fun getValue(string: String): BigInteger? {
    return if (string.matches(OperandType.SINGLE_NUMBER.regex)) {
        string.toBigInteger()
    } else if (string.matches(OperandType.IDENTIFIER.regex)) {
        if (variables.containsKey(string)) {
            variables[string]
        } else {
            println("Unknown variable")
            null
        }
    } else {
        println("Invalid identifier")
        null
    }
}

const val DEBUG = false

fun main() {
    inputLoop@while (true) {
        val line = readLine()!!.trim()
        when (InputType.valueOfOrNull(line)) {
            InputType.LINE -> {
                var remainder = line

                val postFix = mutableListOf<OperandOrOperator>()
                val operators = LinkedList<Operator>()
                var level = 0
                lineLoop@while (remainder.isNotEmpty()) {
                    if (DEBUG)
                        println("Remainder: $remainder")
                    for (operator in Operator.values().reversed()) {
                        val matchResult = operator.regex.find(remainder)
                        if (matchResult != null && matchResult.groups[0]!!.range.first == 0) {
                            if (DEBUG)
                                println("Operator: ${operator.name}")
                            if (operators.isEmpty())
                                operators.push(operator)
                            else if (operator == Operator.LEFT_PARENTHESIS) {
                                operators.push(operator)
                                level++
                            } else if (operator == Operator.RIGHT_PARENTHESIS) {
                                level--
                                try {
                                    while (operators.peek() != Operator.LEFT_PARENTHESIS) {
                                        postFix.add(operators.pop())
                                    }
                                    operators.pop()
                                } catch (e: NoSuchElementException) {
                                    break@lineLoop
                                }
                            } else {
                                while (operators.isNotEmpty() && operators.peek() >= operator) {
                                    postFix.add(operators.pop())
                                }
                                operators.push(operator)
                            }
                            if (DEBUG)
                                println("Operators: $operators")
                            remainder = remainder.substring(matchResult.groups[0]!!.value.length).trim()
                            continue@lineLoop
                        }
                    }
                    for (operandType in OperandType.values()) {
                        val matchResult = operandType.regex.find(remainder)
                        if (matchResult != null && matchResult.groups[0]!!.range.first == 0) {
                            val operand = operandType.toOperand(matchResult.groups[0]!!.value)
                            if (DEBUG)
                                println("Operand: $operand")
                            postFix.add(operand)
                            remainder = remainder.substring(matchResult.groups[0]!!.value.length).trim()
                            continue@lineLoop
                        }
                    }
                    level = -1
                    break
                }
                if (level != 0) {
                    println("Invalid expression")
                    continue
                }
                while (operators.isNotEmpty()) {
                    postFix.add(operators.pop())
                }
                if (DEBUG)
                    println("PostFix: ${postFix.joinToString(" ")}")

                val calculationStack = LinkedList<BigInteger>()
                while (postFix.isNotEmpty()) {
                    val o = postFix.removeAt(0)
                    if (o is Operand) {
                        calculationStack.push(o.getCurrentValue() ?: continue@inputLoop)
                    } else if (o is Operator) {
                        try {
                            val b = calculationStack.pop()
                            val a = calculationStack.pop()
                            calculationStack.push(o.operation.invoke(a, b))
                        } catch (e: NoSuchElementException) {
                            println("Invalid expression")
                            continue@inputLoop
                        }
                    }
                }
                println(calculationStack.pop())
            }
            InputType.SINGLE_NUMBER -> {
                println(getValue(line.trim()))
            }
            InputType.ASSIGNMENT -> {
                val (lhs, rhs) = "\\s*=\\s*".toRegex().split(line, 2).map { it.trim() }
                if (! lhs.matches(OperandType.IDENTIFIER.regex)) {
                    println("Invalid identifier")
                    continue
                }
                variables[lhs] = getValue(rhs) ?: continue
            }
            InputType.EMPTY_LINE -> continue
            InputType.HELP -> println("The program calculates the sum of numbers")
            InputType.EXIT -> {
                println("Bye!")
                return
            }
            else -> println(if (line.startsWith("/")) "Unknown command" else "Invalid expression")
        }
    }
}
