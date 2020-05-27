import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.apache.commons.lang3.StringUtils;

import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

public class Main {

    static final String DUMMY_VAR = "v"; // placeholder variable name to be replaced with letters later
    static final double GOLDEN_RATIO=1.618033988749894848204586834365638117720309179805762862135448622705260462818902449707207204189391137484754088075386891752126633862223536931793180060766726d;
    public static void main(String[] args) {
        double[] constants = new double[]{Math.PI * 2, Math.PI, Math.E, GOLDEN_RATIO /*golden ratio*/, 1, 2, 3, 4, 5, 6};
        List<String>[] allSumStrings = generateAllSumStrings(14, 3, new String[]{"log(", "sqrt("});
        Map<Double, String> result = generateAllSums(allSumStrings, constants);
        try {
            printSqlInsertStatements(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<String>[] generateAllSumStrings(int maxLength, int numVars, String[] specialOperations) {
        Map<Character, List<String>> validMap = generateValidSequenceMap(specialOperations);

        List<String> allStrings = new ArrayList<>();
        recursivelyGenerateEquationStrings("", allStrings, maxLength, numVars, validMap);
        System.out.println("Raw equation strings: " + allStrings.size());

        // we have a list of mostly-broken equations and now can use exp4j to validate them
        List<String> validEquationStrings = new ArrayList<>();
        for (String s : allStrings) {
            try {
                ExpressionBuilder expressionBuilder = new ExpressionBuilder(s);
                if (s.contains(DUMMY_VAR)) {
                    expressionBuilder.variable(DUMMY_VAR);
                }
                if (!expressionBuilder.build().validate(false).isValid()) {
                    continue;
                }
                validEquationStrings.add(s);
            } catch (IllegalArgumentException | EmptyStackException e) {
                // These are the exceptions I expect since these are terrible random equations, we can ignore these bad equations.
            }
        }

        List<String>[] result = tokenise(validEquationStrings, numVars);
        removeDuplicates(result);
        System.out.println("final list: " + Arrays.stream(result).mapToLong(List::size).sum());
        // the returned list is split into multiple lists based on how many variables it uses
        return result;
    }

    private static void removeDuplicates(List<String>[] validExpressions) {
        Map<Double, String> valueMap = new HashMap<>();
        List<String> duplicates = new ArrayList<>();
        // list of 3 number to check with to see if any sums are equivalent. EG log(x*y) vs log(x) + log(y)
        double[] checkNumbers = new double[]{Math.E, Math.PI, GOLDEN_RATIO};
        for (int expressionIndex = 0; expressionIndex < validExpressions.length; expressionIndex++) {
            // the expression index is also the number of variables used in the sum -1
            List<String> list = validExpressions[expressionIndex];
            for (String expressionString : list) {
                ExpressionBuilder expressionBuilder = new ExpressionBuilder(expressionString);
                for (int rep = 0; rep <= expressionIndex; rep++) {
                    expressionBuilder.variable(getVariableName(rep));
                }
                Expression expression = expressionBuilder.build();
                for (int rep = 0; rep <= expressionIndex; rep++) {
                    expression.setVariable(getVariableName(rep), checkNumbers[rep]);
                }
                double result = expression.evaluate();

                result = round(result);

                String existing = valueMap.get(result);
                if (existing != null) {
                    if (expressionString.length() < existing.length()) {
                        duplicates.add(existing);
                        valueMap.put(result, expressionString);
                    } else {
                        duplicates.add(expressionString);
                    }
                    continue;
                }
                valueMap.put(result, expressionString);

            }
        }

        System.out.println("duplicates found: " + duplicates.size());
        for (List<String> list : validExpressions) {
            list.removeAll(duplicates);
        }
    }

    private static void recursivelyGenerateEquationStrings(String current, Collection<String> allStrings, int maxLength, int numVars, Map<Character, List<String>> available) {
        // recursively generate all valid-ish equations, throwing out early any obviously bad ones
        if (!checkValidity(current, maxLength, numVars)) return;
        Character first = null;
        if (current.length() > 0) {
            first = current.charAt(current.length() - 1);
        }
        for (String s : available.get(first)) {
            recursivelyGenerateEquationStrings(current + s, allStrings, maxLength, numVars, available);
        }
        allStrings.add(current);
    }

    private static boolean checkValidity(String test, int maxLength, int numVars) {
        if (test.length() > maxLength) {
            return false;
        }
        if (StringUtils.countMatches(test, DUMMY_VAR) > numVars) {
            return false;
        }
        if (test.matches(".*[^g20t]\\(v\\).*")) {
            return false;
        }
        if (test.startsWith("(v)")) {
            return false;
        }
        return true;
    }

    private static List<String>[] tokenise(List<String> validStrings, int numVars) {
        // tokenise the DUMMY_VAR into x, y, z etc
        List<String>[] result = new ArrayList[numVars];
        for (int i = 0; i < result.length; i++) {
            result[i] = new ArrayList<>();
        }
        for (String s : validStrings) {
            for (int i = 0; i < numVars; i++) {
                s = s.replaceFirst(DUMMY_VAR, getVariableName(i));
            }
            for (int i = numVars - 1; i >= 0; i--) {
                if (s.contains(getVariableName(i))) {
                    result[i].add(s);
                    break;
                }
            }
        }
        return result;
    }

    private static Map<Character, List<String>> generateValidSequenceMap(String[] specialOperations) {
        // generate a map of character to next potentially valid token
        // for example, any variable or a closing brace can be proceeded by +,-,*,^,/,)
        Map<Character, List<String>> validMap = new HashMap<>();
        {
            // v, )
            char[] chars = new char[]{'v', ')'};
            List<String> nexts = Arrays.asList("+", "-", "*", "^", "/", ")");
            for (char c : chars) {
                validMap.put(c, nexts);
            }
        }

        {
            // (, +, -, *, /, ^
            char[] chars = new char[]{'(', '+', '-', '*', '/', '^'};
            List<String> nexts = new ArrayList<>(Arrays.asList(DUMMY_VAR, "("));
            nexts.addAll(Arrays.asList(specialOperations));
            for (char c : chars) {
                validMap.put(c, nexts);
            }
        }
        {
            // start
            List<String> nexts = new ArrayList<>(Arrays.asList(DUMMY_VAR, "("));
            nexts.addAll(Arrays.asList(specialOperations));
            validMap.put(null, nexts);

        }
        return validMap;
    }

    private static String getVariableName(int index) {
        if (index > 10) throw new RuntimeException("10 variables max");
        return (char) ('z' - index) + "";
    }

    private static Map<Double, String> generateAllSums(List<String>[] allLists, double[] constants) {
        // create every replacement combination of tokens from all expressions and the list of constants
        // eg x+(y-z) -> 1+(pi-3), 4+(e-phi)... etc
        Map<Double, String> allResults = new HashMap<>();
        for (int listIndex = 0; listIndex < allLists.length; listIndex++) {
            List<String> expressions = allLists[listIndex];
            for (String expString : expressions) {
                ExpressionBuilder expressionBuilder = new ExpressionBuilder(expString);
                for (int varIndex = 0; varIndex <= listIndex; varIndex++) {
                    expressionBuilder.variable(getVariableName(varIndex));
                }
                Expression expression = expressionBuilder.build();
                // luckily because we want ALL combinations, including 000, 001, 002... etc, we can just use the counting numbers in the correct radix
                int maxCombineIndex = (int) Math.pow(constants.length, listIndex + 1);
                for (int combineIndex = 0; combineIndex < maxCombineIndex; combineIndex++) {
                    String variableIndexString = Integer.toString(combineIndex, constants.length);
                    while (variableIndexString.length() < listIndex + 1) {
                        // pad with zeroes because we want 0000 instead of 0
                        // too lazy to use StringBuilder. Sue me.
                        variableIndexString = variableIndexString + "0";
                    }
                    String equation = expString;
                    for (int varIndex = 0; varIndex <= listIndex; varIndex++) {
                        // use the string to index into the list of variables
                        int valueIndex = Integer.parseInt(variableIndexString.charAt(varIndex) + "", constants.length);
                        expression.setVariable(getVariableName(varIndex), constants[valueIndex]);
                        equation = equation.replace(getVariableName(varIndex), getConstantName(constants[valueIndex]));
                    }
                        // evaluate the expression, finally!
                    try {
                        double res = expression.evaluate();

                        if (Double.isInfinite(res)) continue;
                        if (Double.isNaN(res)) continue;

                        res = round(res);
                        int max = 1000; // max of 1000 is probably fine here
                        if (res > max || res < -max) continue;
                        if ((int) res == res) continue; //skip if integer, boring

                        // check if there is a simpler version already
                        String existingEquation = allResults.get(res);
                        if (existingEquation == null) {
                            allResults.put(res, equation);
                        } else if (equation.length() < existingEquation.length()) {
                            allResults.put(res, equation);
                        }
                    } catch (ArithmeticException e) {
                        // catch expression evaluation exceptions and skip
                    }
                }
            }
        }
        return allResults;
    }

    private static CharSequence getConstantName(double constant) {
        // get the textual representation
        if(constant == GOLDEN_RATIO) return "phi";
        if(constant == Math.E) return "e";
        if(constant == Math.PI) return "pi";
        if(constant == Math.PI*2) return "tau";
        return (int)constant+"";
    }

    private static void printSqlInsertStatements(Map<Double, String> result) throws IOException {
        DecimalFormat df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        df.setMaximumFractionDigits(100);
        FileWriter myWriter = new FileWriter("strongarm.sql");
        result.forEach((value, equation) -> {
                    try {
                        myWriter.write("insert into strongarm (equation, result) values ('" + equation + "'," + df.format(value) + ");\n");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
        );
        myWriter.close();
    }

    private static double round(double val) {
        // exp4j gets inaccurate around 13 decimal places
        double mult = Math.pow(10, 13);
        val = val * mult;
        val = Math.round(val);
        return val / mult;
    }
}
