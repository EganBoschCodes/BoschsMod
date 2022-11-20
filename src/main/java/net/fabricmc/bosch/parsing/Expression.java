package net.fabricmc.bosch.parsing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

public abstract class Expression {

    public static Map<String, BiFunction<Maybe, Maybe, Maybe>> operators;
    public static Map<String, BiFunction<ArrayList<Expression>, Map<String, Float>, Maybe>> functions;

    static {
        operators = new HashMap<>();
        operators.put("+",  Maybe.map((a, b) -> a + b));
        operators.put("-",  Maybe.map((a, b) -> a - b));
        operators.put("*",  Maybe.map((a, b) -> a * b));
        operators.put("/",  Maybe.map((a, b) -> a / b));
        operators.put("^",  Maybe.map((a, b) -> (float)Math.pow(a, b)));
        operators.put("&&",  Maybe.map((a, b) -> (a != 0.0f && b != 0.0f) ? 1.0f : 0.0f));
        operators.put("||",  Maybe.map((a, b) -> (a != 0.0f || b != 0.0f) ? 1.0f : 0.0f));
        operators.put(">",  Maybe.map((a, b) -> (a > b) ? 1.0f : 0.0f));
        operators.put("<",  Maybe.map((a, b) -> (a < b) ? 1.0f : 0.0f));
        operators.put("%",  Maybe.map((a, b) -> (float)(a - Math.floor(a / b) * b)));

        functions = new HashMap<>();
        functions.put("sqrt",  (args, vars) -> Maybe.mapDouble(Math::sqrt).apply(args.get(0).evaluate(vars)));
        functions.put("sin",  (args, vars) -> Maybe.mapDouble(Math::sin).apply(args.get(0).evaluate(vars)));
        functions.put("cos",  (args, vars) -> Maybe.mapDouble(Math::cos).apply(args.get(0).evaluate(vars)));
        functions.put("atan",  (args, vars) -> Maybe.mapDouble(Math::atan).apply(args.get(0).evaluate(vars)));
        functions.put("atan2",  (args, vars) -> Maybe.mapDouble(Math::atan2).apply(args.get(0).evaluate(vars), args.get(1).evaluate(vars)));
        functions.put("min",  (args, vars) -> Maybe.mapDouble(Math::min).apply(args.get(0).evaluate(vars), args.get(1).evaluate(vars)));
        functions.put("mag",  (args, vars) -> args.size() == 2 ? Maybe.map((x, y) -> (float)Math.sqrt(x*x + y*y)).apply(args.get(0).evaluate(vars), args.get(1).evaluate(vars)) : Maybe.map((x, y, z) -> (float)Math.sqrt(x*x + y*y + z*z)).apply(args.get(0).evaluate(vars), args.get(1).evaluate(vars), args.get(2).evaluate(vars)));
        functions.put("within",  (args, vars) -> Maybe.map((x, y, z) -> Math.abs(x - y) < z ? 1.0f : 0.0f).apply(args.get(0).evaluate(vars), args.get(1).evaluate(vars), args.get(2).evaluate(vars)));
        functions.put("random",  (args, vars) -> new Maybe((float) Math.random()));
        functions.put("exp",  (args, vars) -> Maybe.mapDouble(Math::exp).apply(args.get(0).evaluate(vars)));
        functions.put("abs",  (args, vars) -> Maybe.mapDouble(Math::abs).apply(args.get(0).evaluate(vars)));
    }


    public abstract Maybe evaluate(Map<String, Float> variables);

    public static Expression parse (String s) {
        s = s.replaceAll("\\s", "");
        if (s.length() == 0) return new FloatExpr(Maybe.no("Empty string passed to parser!"));

        //System.out.println(s);
        while(s.charAt(0) == '(' && s.charAt(s.length() - 1) == ')') {s = s.substring(1, s.length() - 1); }

        try { return new FloatExpr(Maybe.yes(Float.parseFloat(s))); } catch (Exception ignored) {}
        if (!Pattern.compile("[^a-zA-Z]").matcher(s).find()) return new SymbolExpr(s);
        if (s.charAt(0) == '-' && !Pattern.compile("[^a-zA-Z]").matcher(s.substring(1)).find()) {
            Expression left = new FloatExpr(Maybe.yes(-1.0f));
            Expression right = new SymbolExpr(s.substring(1));
            return new OperatorExpr(left, right, "*");
        }

        Expression e;
        if ((e = splitOnOperators(s, new String[]{"&&", "||"})) != null) return e;
        if ((e = splitOnOperators(s, new String[]{">", "<"})) != null) return e;
        if ((e = splitOnOperators(s, new String[]{"+", "-"})) != null) return e;
        if ((e = splitOnOperators(s, new String[]{"*", "/"})) != null) return e;
        if ((e = splitOnOperators(s, new String[]{"^"})) != null) return e;
        if ((e = splitOnOperators(s, new String[]{"%"})) != null) return e;

        int parenIndex = s.indexOf('(');
        if (parenIndex < 0 || s.charAt(s.length() - 1) != ')') { return new FloatExpr(Maybe.no("Invalid Value passed to parser! (" + s + ")")); }

        String functionName = s.substring(0, parenIndex);
        if (!functions.containsKey(functionName)) { return new FloatExpr(Maybe.no("Invalid Function passed to parser! (" + functionName + ")")); }

        String[] argStrings = s.substring(parenIndex + 1, s.length() - 1).split(",");
        ArrayList<Expression> args = new ArrayList<>();
        for(String arg : argStrings) {
            args.add(parse(arg));
        }

        return new FunctionExpr(args, functionName);
    }

    public static Map<String, Expression> parseAll(String s) {
        String[] split = s.split(";");
        Map<String, Expression> returnVal = new HashMap<>();
        for(String var : split) {
            String[] varSplit = var.split("=");
            returnVal.put(varSplit[0], parse(varSplit[1]));
        }
        return returnVal;
    }

    public static Map<String, Maybe> evaluateAll(Map<String, Expression> exprs, Map<String, Float> variables) {
        Map<String, Maybe> returnVal = new HashMap<>();
        for(Map.Entry<String, Expression> entry : exprs.entrySet()) {
            returnVal.put(entry.getKey(), entry.getValue().evaluate(variables));
        }
        return returnVal;
    }

    public static Map<String, Float> clean(Map<String, Maybe> exprs) {
        Map<String, Float> returnVal = new HashMap<>();
        for(Map.Entry<String, Maybe> entry : exprs.entrySet()) {
            if (entry.getValue().is) returnVal.put(entry.getKey(), entry.getValue().val);
        }
        return returnVal;
    }

    private static Expression splitOnOperators(String s, String[] ops) {
        int minIndex = s.length();
        String op = "";
        for(String o : ops) {
            int index = topLevelIndexOf(s, o);
            if (index >= 0 && index < minIndex) {
                minIndex = index;
                op = o;
            }
        }
        if(minIndex < s.length()) {
            Expression left = parse(s.substring(0, minIndex));
            Expression right = parse(s.substring(minIndex + 1));
            if (left instanceof FloatExpr && right instanceof FloatExpr) {
                return new FloatExpr(operators.get(op).apply(left.evaluate(null), right.evaluate(null)));
            }
            return new OperatorExpr(left, right, op);
        }
        return null;
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

    public static class Maybe {
        public boolean is = false;
        public float val;
        public String err;
        private Maybe (float f) {
            val = f;
            is = true;
        }
        private Maybe (String e) {err = e;}

        public static Maybe no(String err) {
            return new Maybe(err);
        }
        public static Maybe yes(float f) {
            return new Maybe(f);
        }

        public String toString() {
            return "{ is: " + is + (is ? " , val: " + val + " }" : " , err: " + err + " }");
        }

        public static Function<Maybe, Maybe> mapDouble (Function<Double, Double> func) { return (a) -> {return a.is ? Maybe.yes(func.apply((double)a.val).floatValue()) : a;}; }
        public static BiFunction<Maybe, Maybe, Maybe> mapDouble (BiFunction<Double, Double, Double> func) { return (a, b) -> {return a.is && b.is ? Maybe.yes(func.apply((double)a.val, (double)b.val).floatValue()) : !a.is ? a : b;}; }
        public static TriFunction<Maybe> mapDouble (TriFunction<Double> func) { return (a, b, c) -> {return a.is && b.is && c.is ? Maybe.yes(func.apply((double)a.val, (double)b.val, (double)c.val).floatValue()) : !a.is ? a : !b.is ? b : c;}; }

        public static Function<Maybe, Maybe> map (Function<Float, Float> func) { return (a) -> {return a.is ? Maybe.yes(func.apply(a.val)) : a;}; }
        public static BiFunction<Maybe, Maybe, Maybe> map (BiFunction<Float, Float, Float> func) { return (a, b) -> {return a.is && b.is ? Maybe.yes(func.apply(a.val, b.val)) : !a.is ? a : b;}; }

        public static TriFunction<Maybe> map (TriFunction<Float> func) { return (a, b, c) -> {return a.is && b.is && c.is ? Maybe.yes(func.apply(a.val, b.val, c.val)) : !a.is ? a : !b.is ? b : c;}; }
    }

    @FunctionalInterface
    private interface TriFunction<T> {
        T apply(T t, T u, T v);
    }

    private static class FloatExpr extends Expression {
        private final Maybe value;

        public FloatExpr(Maybe f) { value = f; }
        @Override
        public Maybe evaluate(Map<String, Float> variables) { return value; }

        @Override
        public String toString() { return value.toString(); }
    }

    private static class SymbolExpr extends Expression {
        private final String symbol;

        public SymbolExpr(String s) { symbol = s; }
        @Override
        public Maybe evaluate(Map<String, Float> variables) {
            return variables.containsKey(symbol) ? Maybe.yes(variables.get(symbol)) : Maybe.no("Variable \"" + symbol +"\" is not defined!");
        }

        @Override
        public String toString() { return "{" + symbol + "}"; }
    }

    private static class OperatorExpr extends Expression {
        private final Expression left;
        private final Expression right;
        private final String operatorTag;

        public OperatorExpr(Expression l, Expression r, String o) {
            left = l;
            right = r;
            operatorTag = o;
        }
        @Override
        public Maybe evaluate(Map<String, Float> variables) {
            return operators.get(operatorTag).apply(left.evaluate(variables), right.evaluate(variables));
        }

        @Override
        public String toString() {
            return "{ " + left.toString() + " " + operatorTag + " " + right.toString() + " }";
        }
    }

    private static class FunctionExpr extends Expression {

        private final ArrayList<Expression> args;
        private final String functionName;

        public FunctionExpr(ArrayList<Expression> arguments, String function) {
            args = arguments;
            functionName = function;
        }
        @Override
        public Maybe evaluate(Map<String, Float> variables) {
            return functions.get(functionName).apply(args, variables);
        }

        public String toString() {
            StringBuilder s = new StringBuilder("{ " + functionName + "(");
            for(Expression e : args) {
                s.append(e.toString()).append(", ");
            }
            return s.substring(0, s.length() - 2) + ") }";
        }
    }
}
