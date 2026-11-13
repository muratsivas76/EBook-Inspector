package net.murat.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class Calculator {

  // Private constructor to prevent instantiation
  private Calculator() {
    // This utility class should not be instantiated
  }

  /**
  * Performs basic arithmetic operations (+, -, *, /) on a given input string.
  *
  * @param input The mathematical expression (e.g., "5+3", "10x2", "5÷2")
  * @return The result formatted to 2 decimal places as a String, or an error message.
  */
  public static String calculate(String input) {
    if (input == null || input.trim().isEmpty()) {
      return "0.00";
    }

    // Clean spaces and normalize operators
    String expression = input.replaceAll("\\s+", "");
    expression = expression.replace('x', '*');
    expression = expression.replace('÷', '/');

    // Find the operator and its position
    char operator = ' ';
    int operatorIndex = -1;

    // Look for operators sequentially
    if ((operatorIndex = expression.indexOf('+')) != -1) {
      operator = '+';
    } else if ((operatorIndex = expression.indexOf('-')) != -1) {
      // Handle negative numbers at the beginning
      if (operatorIndex == 0) {
        operatorIndex = expression.indexOf('-', 1);
      }
      if (operatorIndex != -1) {
        operator = '-';
      }
    } else if ((operatorIndex = expression.indexOf('*')) != -1) {
      operator = '*';
    } else if ((operatorIndex = expression.indexOf('/')) != -1) {
      operator = '/';
    }

    // If no valid operator is found, return error
    if (operatorIndex == -1 || operator == ' ') {
      return "Invalid Expression";
    }

    try {
      // Split the numbers based on the operator position
      String part1 = expression.substring(0, operatorIndex);
      String part2 = expression.substring(operatorIndex + 1);

      double num1 = Double.parseDouble(part1);
      double num2 = Double.parseDouble(part2);
      double result = 0.0;

      // Execute the operation
      switch (operator) {
        case '+':
        result = num1 + num2;
        break;
        case '-':
        result = num1 - num2;
        break;
        case '*':
        result = num1 * num2;
        break;
        case '/':
        if (num2 == 0) {
          return "Error: Div by 0";
        }
        result = num1 / num2;
        break;
        default:
        return "Invalid Operator";
      }

      // Format the result to exactly 2 decimal places using US locale (dot separator)
      DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
      DecimalFormat df = new DecimalFormat("#.00", symbols);

      // Handle cases where the result is a whole number like .00 to match standard display
      if (result == 0.0) {
        return "0.00";
      }

      return df.format(result);

    } catch (NumberFormatException e) {
      // Return error if parsing fails
      return "Parsing Error";
    }
  }

}
