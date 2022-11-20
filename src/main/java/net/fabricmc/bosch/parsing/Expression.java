package net.fabricmc.bosch.parsing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class Expression {

    public static String extractExpr(String s, String valTag) {
        s = s.replaceAll("\\s", "");

        String[] vals = s.split(";");
        for (String val : vals) {
            String[] valSplit = val.split("=");
            if (valSplit[0].equals(valTag)) {
                return valSplit[1];
            }
        }
        return "";
    }

    public static Map<String, Float> clean(Map<String, Maybe> vals) {
        Map<String, Float> cleanVals = new HashMap<>();
        for(Map.Entry<String, Maybe> entry : vals.entrySet()) {
            if (entry.getValue().is) cleanVals.put(entry.getKey(), entry.getValue().val);
        }
        return cleanVals;
    }

    public static Map<String, Maybe> extractAllValues(String s, Map<String, Float> values) {
        s = s.replaceAll("\\s", "");

        Map<String, Maybe> valMap = new HashMap<>();
        String[] vals = s.split(";");
        for (String val : vals) {
            String[] valSplit = val.split("=");
            Maybe evaledVal = parse(valSplit[1], values);
            valMap.put(valSplit[0], evaledVal);
            if (evaledVal.is) values.put(valSplit[0], evaledVal.val);
        }
        return valMap;
    }

    public static Maybe parse(String s, Map<String, Float> values) {
        values.put("pi", (float)Math.PI);
        values.put("e", (float)Math.E);

        return evaluate(s.replaceAll("\\s", ""), values);
    }

    private static Maybe evaluate(String s, Map<String, Float> values) {

        //System.out.println(s);

        try { return Maybe.yes(Float.parseFloat(s)); } catch (Exception ignored) {}
        if (values.containsKey(s)) return Maybe.yes(values.get(s));
        if (s.length() == 0) return Maybe.no();
        if (s.charAt(0) == '-' && values.containsKey(s.substring(1))) return Maybe.yes(-values.get(s.substring(1)));

        Maybe greater = applyOperation(s, values, ">", (a, b) -> {return a > b ? 1.0f : 0.0f;});
        if (greater.is) return greater;

        Maybe less = applyOperation(s, values, "<", (a, b) -> {return a < b ? 1.0f : 0.0f;});
        if (less.is) return less;

        Maybe and = applyOperation(s, values, "&&", (a, b) -> {return (a != 0.0f && b != 0.0f) ? 1.0f : 0.0f;});
        if (and.is) return and;

        Maybe or = applyOperation(s, values, "||", (a, b) -> {return (a != 0.0f || b != 0.0f) ? 1.0f : 0.0f;});
        if (or.is) return or;

        Maybe pow = applyOperation(s, values, "^", (a, b) -> {return (float)Math.pow(a, b);});
        if (pow.is) return pow;

        Maybe mod = applyOperation(s, values, "%", (a, b) -> {return (float)(a - Math.floor(a / b) * b);});
        if (mod.is) return mod;

        Maybe mult = applyOperation(s, values, "*", (a, b) -> {return a * b;});
        if (mult.is) return mult;

        Maybe div = applyOperation(s, values, "/", (a, b) -> {return a / b;});
        if (div.is) return div;

        Maybe add = applyOperation(s, values, "+", (a, b) -> {return a + b;});
        if (add.is) return add;

        Maybe sub = applyOperation(s, values, "-", (a, b) -> {return a - b;});
        if (sub.is) return sub;

        if (s.contains("(")) {
            int parenIndex = s.indexOf("(");
            if (parenIndex > 0 && orderOfOp(s.charAt(parenIndex - 1)) < 0) {
                int funcIter = parenIndex - 1;
                while (funcIter >= 0 && orderOfOp(s.charAt(funcIter)) < 0) {
                    funcIter--;
                }

                String funcName = s.substring(funcIter + 1,parenIndex);

                String innards = captureParenthesis(s, s.indexOf('('));
                ArrayList<String> splitInnards = new ArrayList<>();
                int argStart = 0;
                int argEnd = 0;
                int parenCount = 0;
                while(argEnd < innards.length()) {
                    if(innards.charAt(argEnd) == '(') {
                        parenCount++;
                    }
                    if(innards.charAt(argEnd) == ')') {
                        parenCount--;
                    }
                    if(parenCount == 0 && innards.charAt(argEnd) == ',') {
                        splitInnards.add(innards.substring(argStart, argEnd));
                        argStart = argEnd + 1;
                    }
                    argEnd++;
                }
                splitInnards.add(innards.substring(argStart, argEnd));
                float[] evaledArgs = new float[splitInnards.size()];
                for(int i = 0; i < splitInnards.size(); i++){
                    Maybe evaledArg = evaluate(splitInnards.get(i), values);
                    if (!evaledArg.is) return Maybe.no();
                    evaledArgs[i] = evaledArg.val;
                }

                String before = s.substring(0, s.indexOf('(') - funcName.length());
                String after = s.substring(s.indexOf('(') + innards.length() + 2);

                Maybe returnVal = Maybe.no();

                if (funcName.equals("sqrt")) {
                    returnVal = evaluate( before + Math.sqrt((double)evaledArgs[0]) + after, values);
                }

                if (funcName.equals("sin")) {
                    returnVal = evaluate( before + Math.sin((double)evaledArgs[0]) + after, values);
                }

                if (funcName.equals("cos")) {
                    returnVal = evaluate( before + Math.cos((double)evaledArgs[0]) + after, values);
                }

                if (funcName.equals("atan")) {
                    returnVal = evaluate( before + Math.atan((double)evaledArgs[0]) + after, values);
                }

                if (funcName.equals("atan2")) {
                    returnVal = evaluate( before + Math.atan2((double)evaledArgs[0], (double)evaledArgs[1]) + after, values);
                }

                if (funcName.equals("mag")) {
                    if(evaledArgs.length == 2)  { returnVal = evaluate( before + Math.sqrt((double)(evaledArgs[0] * evaledArgs[0] + evaledArgs[1] * evaledArgs[1])) + after, values); }
                    else { returnVal = evaluate( before + Math.sqrt((double)(evaledArgs[0] * evaledArgs[0] + evaledArgs[1] * evaledArgs[1] + evaledArgs[2] * evaledArgs[2])) + after, values); }
                }

                if (funcName.equals("within")) {
                    returnVal = evaluate( before + (Math.abs(evaledArgs[0] - evaledArgs[1]) < evaledArgs[2] ? 1.0 : 0.0) + after, values);
                }

                if(funcName.equals("random")) {
                    returnVal = evaluate( before + (Math.random()) + after, values);
                }

                if(funcName.equals("exp")) {
                    returnVal = evaluate( before + (Math.exp(evaledArgs[0])) + after, values);
                }

                if(funcName.equals("abs")) {
                    returnVal = evaluate( before + (Math.abs(evaledArgs[0])) + after, values);
                }

                if (funcName.equals("min")) {
                    returnVal = evaluate( before + Math.min(evaledArgs[0], evaledArgs[1]) + after, values);
                }

                if (funcName.equals("max")) {
                    returnVal = evaluate( before + Math.max(evaledArgs[0], evaledArgs[1]) + after, values);
                }

                if (returnVal.is) values.put(funcName+'('+innards+')', returnVal.val);

                return returnVal;
            }

            String innards = captureParenthesis(s, s.indexOf('('));
            Maybe evaledParenthesis = evaluate(innards, values);
            if (!evaledParenthesis.is) {
                return Maybe.no();
            }

            String before = s.substring(0, s.indexOf('('));
            String after = s.substring(s.indexOf('(') + innards.length() + 2);

            return evaluate( before + evaledParenthesis.val + after, values);
        }

        return Maybe.no();
    }


    private static int orderOfOp(char operation) {
        if(operation == '+' || operation == '-') {
            return 0;
        }
        if(operation == '*' || operation == '/') {
            return 1;
        }
        if(operation == '%') {
            return 2;
        }
        if(operation == '^') {
            return 3;
        }
        if(operation == '&' || operation == '|') {
            return 4;
        }
        if(operation == '>' || operation == '<') {
            return 5;
        }
        return -1;
    }

    private static Maybe applyOperation(String s, Map<String, Float> values, String operator, BiFunction<Float, Float, Float> logic) {
        int index = topLevelIndexOf(s, operator);
        if(index >= 0) {
            String[] breakDown = captureOperation(s, operator, index);
            Maybe val1 = evaluate(breakDown[1], values);
            if (!val1.is) return Maybe.no();

            Maybe val2 = evaluate(breakDown[2], values);
            if (!val2.is) return Maybe.no();

            float value = logic.apply(val1.val, val2.val);

            values.put(breakDown[1] + operator + breakDown[2], value);
            if(breakDown[0].length() + breakDown[3].length() > 0) {
                return evaluate(breakDown[0] + value + breakDown[3], values);
            }
            return Maybe.yes(value);
        }
        return Maybe.no();
    }

    private static String[] captureOperation(String s, String operator, int index) {
        int order = orderOfOp(operator.charAt(0));
        int leftIter = index - 1;
        int leftParens = 0;
        while(leftIter >= 0 && ((orderOfOp(s.charAt(leftIter)) == order || orderOfOp(s.charAt(leftIter)) < 0) || leftParens > 0)) {
            if(s.charAt(leftIter) == ')') {
                leftParens++;
            }
            if(s.charAt(leftIter) == '(') {
                leftParens--;
            }
            leftIter--;
        }
        if(leftIter >= 0 && s.charAt(leftIter) == '-' && (leftIter == 0 || orderOfOp(s.charAt(leftIter - 1)) > 0)) {
            leftIter--;
        }
        int rightIter = index + operator.length();
        int rightParens = 0;
        while(rightIter < s.length()  && (rightParens > 0 || (orderOfOp(s.charAt(rightIter)) == order || orderOfOp(s.charAt(rightIter)) < 0) || (s.charAt(rightIter) == '-' && rightIter == index + operator.length()))) {
            if(s.charAt(rightIter) == ')') {
                rightParens--;
            }
            if(s.charAt(rightIter) == '(') {
                rightParens++;
            }
            rightIter++;
        }

        //System.out.println("0: " + s.substring(0, leftIter+1));
        //System.out.println("1: " + s.substring(leftIter+1, index));
        //System.out.println("2: " + s.substring(index+operator.length(), rightIter));
        //System.out.println("3: " + s.substring(rightIter));

        return new String[]{s.substring(0, leftIter+1), s.substring(leftIter+1, index), s.substring(index+operator.length(), rightIter), s.substring(rightIter)};
    }

    private static String captureParenthesis(String s, int index) {
        int openCount = 1;
        int iter = index + 1;
        while(iter < s.length() && openCount > 0) {
            if(s.charAt(iter) == '(') {
                openCount++;
            }
            if(s.charAt(iter) == ')') {
                openCount--;
            }
            iter++;
        }

        return s.substring(index + 1, iter - 1);
    }

    private static int topLevelIndexOf(String s, String operator) {
        int scope = 0;
        for(int search = 0; search <= s.length() - operator.length(); search++) {
            if(s.charAt(search) == '(') {
                scope++;
            }
            if(s.charAt(search) == ')') {
                scope--;
            }
            if(scope == 0 && s.substring(search, search + operator.length()).equals(operator)) {
                return search;
            }
        }
        return -1;
    }

    //A monad is a monoid in the category of endofunctors...
    public static class Maybe {
        public boolean is = false;
        public float val;
        private Maybe (float f) {
            val = f;
            is = true;
        }
        private Maybe () {}

        public static Maybe no() {
            return new Maybe();
        }
        public static Maybe yes(float f) {
            return new Maybe(f);
        }

        public String toString() {
            return "{ is: " + is + (is ? " , val: " + val + " }" : " }");
        }
    }
}
